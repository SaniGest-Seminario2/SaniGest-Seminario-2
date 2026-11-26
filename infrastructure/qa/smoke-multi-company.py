"""Run against the disposable local multi-company Compose stack."""
import json
import os
import threading
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta


def exchange(path, *, token=None, tenant=None, data=None, key=None, status=200,
             origin=None, method=None, content_type="application/json"):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if tenant:
        headers["X-Company-Id"] = tenant
    if key:
        headers["Idempotency-Key"] = key
    body = None
    if data is not None:
        headers["Content-Type"] = content_type
        body = json.dumps(data).encode()
    request = urllib.request.Request(f"{origin or GATEWAY}/api/v1/{path}", body, headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            actual, payload = response.status, response.read()
            response_headers = response.headers
    except urllib.error.HTTPError as response:
        actual, payload = response.code, response.read()
        response_headers = response.headers
    if actual != status:
        try:
            problem = json.loads(payload)
            code = problem.get("errorCode") if isinstance(problem, dict) else None
        except (UnicodeError, ValueError):
            code = None
        raise AssertionError(f"{request.get_method()} {path}: expected {status}, got {actual} ({code})")
    decoded = json.loads(payload) if payload else None
    if status >= 400:
        assert response_headers.get_content_type() == "application/problem+json"
        assert decoded["status"] == status
        required = ("type", "title", "detail", "errorCode", "timestamp", "traceId")
        missing = [field for field in required if not decoded.get(field)]
        assert not missing, f"HTTP {status}: missing problem fields {missing}"
    if status == 204:
        assert not payload
    return decoded


def main():
    suffix = uuid.uuid4().hex
    credentials = {"email": f"tenant-{suffix}@example.test", "password": "Disposable-smoke-454!"}
    exchange("auth/register", data={**credentials, "name": "Multiempresa Smoke"}, status=201)
    token = exchange("auth/login", data=credentials)["accessToken"]
    assert exchange("companies", token=token) == []

    def create_company(label):
        return exchange(
            "companies", token=token, key=uuid.uuid4().hex, status=201,
            data={"companyName": f"Smoke {label} {suffix}", "businessName": f"Smoke {label}",
                  "email": credentials["email"], "phone": "8095550100", "location": "Santo Domingo"},
        )["companyId"]

    a, b = create_company("A"), create_company("B")
    assert {row["companyId"] for row in exchange("companies", token=token)} == {a, b}
    exchange("companies/me", token=token, status=409)
    for invalid in ({"name": "Sin tipo"}, {"name": "Tipo nulo", "clientType": None}):
        problem = exchange("clients", token=token, tenant=a, data=invalid, status=400)
        assert problem["status"] == 400 and problem["errorCode"] == "INVALID_REQUEST"
    assert exchange("clients", token=token, tenant=a)["content"] == []
    assert exchange("clients", token=token, tenant=a, origin=CATALOG)["content"] == []
    assert exchange("sales", token=token, tenant=a)["content"] == []
    exchange("clients", status=401)
    exchange("missing-s2-302-route", token=token, status=404)
    exchange("auth/register", method="POST", status=400)
    exchange("auth/register", token=token, status=405)
    exchange("auth/register", data={}, content_type="text/plain", status=415)
    client = exchange("clients", token=token, tenant=a, data={
        "name": "Cliente A", "clientType": "NATURAL", "paymentTerms": "BOTH",
    }, status=201)
    client_a = client["id"]
    assert client["countryCode"] == "DO" and client["paymentTerms"] == "BOTH" and client["categoryId"]
    assert exchange(f"clients/{client_a}", token=token, tenant=a)["clientType"] == "NATURAL"
    edited = exchange(f"clients/{client_a}", token=token, tenant=a, method="PATCH",
                      data={"phone": "8095550100"})
    assert edited["phone"] == "8095550100" and edited["categoryId"] == client["categoryId"]
    assert exchange("clients?clientType=NATURAL", token=token, tenant=a)["content"][0]["phone"] == edited["phone"]
    supplier = exchange("suppliers", token=token, tenant=a, status=201,
                        data={"name": "Proveedor A", "contact": "Contacto", "supplyType": "PRODUCT"})
    assert supplier["paymentTerms"] == "CASH"
    exchange("catalog-items", token=token, tenant=a, status=400,
             data={"name": "Sin unidad", "type": "PRODUCT", "price": "25.00"})
    client_b = exchange("clients", token=token, tenant=b, data={"name": "Cliente B", "clientType": "LEGAL"}, status=201)["id"]
    item_a = exchange("catalog-items", token=token, tenant=a, status=201, data={
        "name": "Artículo A", "type": "PRODUCT", "price": "25.00", "unitCode": "UNIT",
        "taxClassificationCode": "ITBIS_18", "availableForPurchase": True,
    })["id"]

    exchange(f"suppliers/{supplier['id']}/items", token=token, tenant=a, method="PUT", status=204,
             data={"items": [{"catalogItemId": item_a, "offeredCost": "15.0000", "primary": True}]})
    product = exchange(f"catalog-items/{item_a}", token=token, tenant=a)
    assert product["primarySupplierName"] == supplier["name"] and product["unitDivisible"] is False

    barrier = threading.Barrier(2)

    def query(tenant):
        barrier.wait(timeout=5)
        return {row["id"] for row in exchange("clients", token=token, tenant=tenant)["content"]}

    with ThreadPoolExecutor(max_workers=2) as pool:
        first, second = pool.submit(query, a), pool.submit(query, b)
        assert first.result(timeout=15) == {client_a}
        assert second.result(timeout=15) == {client_b}

    sale = {"clientId": client_a, "lines": [{"catalogItemId": item_a, "quantity": 1}],
            "mode": "CREDIT", "dueDate": (date.today() + timedelta(days=30)).isoformat()}
    sale_key = uuid.uuid4().hex
    created = exchange("sales", token=token, tenant=a, data=sale, key=sale_key, status=201)
    sale_id = created["id"]
    assert exchange("sales", token=token, tenant=a, data=sale, key=sale_key, status=201) == created
    invoice = exchange(f"sales/{sale_id}/invoices", token=token, tenant=a,
                       key=uuid.uuid4().hex, method="POST", status=201)
    detail = exchange(f"sales/{sale_id}", token=token, tenant=a)
    assert detail["invoiceNumber"] == invoice["number"] and detail["paymentStatus"] == "OPEN"
    receivable = exchange(f"receivables/{sale_id}", token=token, tenant=a)
    assert receivable["saleCode"] == created["code"] and receivable["invoiceId"] == invoice["id"]
    assert receivable["outstandingBalance"] == created["total"]
    exchange("sales", token=token, tenant=a, data={**sale, "clientId": client_b},
             key=uuid.uuid4().hex, status=404)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    exchange(f"sales/{sale_id}", token=token, tenant=b, status=404)
    exchange("clients", token=token, status=400)
    exchange("clients", token=token, status=400, origin=CATALOG)
    exchange(f"clients/{client_a}", token=token, tenant=b, status=404, origin=CATALOG)
    verify_inventory(token, a, b, item_a)
    print("Multi-company HTTP smoke passed")


def verify_inventory(token, company, other, item):
    def inventory(path, **kwargs):
        return exchange(path, token=token, tenant=company, **kwargs)

    warehouse = inventory("warehouses", data={"code": "ALM-001", "name": "Principal"}, status=201)["id"]
    position = f"inventory/stock/{item}/{warehouse}"
    assert inventory(position)["availability"] == "NOT_TRACKED"
    initial = {"trackingEnabled": True, "minimumStock": "5.000", "initialQuantity": "20.000"}
    configured = inventory(position, method="PUT", data=initial, key=uuid.uuid4().hex)
    assert configured["onHand"] == "20.000" and configured["warehouseName"] == "Principal"
    assert inventory(position) == configured

    def adjust(article, direction, quantity, key, status=201):
        return inventory("inventory/adjustments", data={
            "catalogItemId": article, "warehouseId": warehouse, "direction": direction,
            "quantity": quantity, "reason": "Corrección smoke"}, key=key, status=status)

    retry_key = uuid.uuid4().hex
    inbound = adjust(item, "IN", "5", retry_key)
    adjust(item, "OUT", "3", uuid.uuid4().hex)
    assert inventory(position)["onHand"] == "22.000"
    batch = inventory(f"inventory/stock?warehouseId={warehouse}&catalogItemIds={item}")
    assert batch["totalElements"] == 1 and batch["content"][0]["warehouseName"] == "Principal"
    assert adjust(item, "IN", "5.000", retry_key) == inbound
    assert inventory(f"inventory/movements?warehouseId={warehouse}")["totalElements"] == 3
    summary = inventory(f"inventory/summary?warehouseId={warehouse}")
    assert summary["activeWarehouses"] == summary["trackedPositions"] == summary["availablePositions"] == 1
    service = inventory("catalog-items", data={"name": "Servicio", "type": "SERVICE", "price": 0,
                                                "unitCode": "UNIT", "taxClassificationCode": "EXEMPT"}, status=201)["id"]
    assert inventory(f"inventory/stock/{service}/{warehouse}")["availability"] == "NOT_APPLICABLE"
    for state, item_type, expected in (("AVAILABLE", "PRODUCT", item), ("NOT_APPLICABLE", "SERVICE", service)):
        path = f"catalog-items?warehouseId={warehouse}&availability={state}&type={item_type}&size=1"
        page = inventory(path)
        assert page["totalElements"] == page["totalPages"] == 1
        assert page["content"][0]["id"] == expected
        exchange(path, token=token, tenant=other, status=404)
    adjust(service, "IN", "1", uuid.uuid4().hex, 400)
    exchange(position, token=token, tenant=other, status=404)
    exchange("inventory/stock", status=401, origin=INVENTORY)
    exchange("inventory/stock", token=token, status=400, origin=INVENTORY)
    assert inventory(f"inventory/movements?warehouseId={warehouse}")["totalElements"] == 3


if __name__ == "__main__":
    GATEWAY = os.environ.get("SMOKE_GATEWAY_URL", "http://127.0.0.1:8085")
    CATALOG = os.environ.get("SMOKE_CATALOG_URL", "http://127.0.0.1:8083")
    INVENTORY = os.environ.get("SMOKE_INVENTORY_URL", "http://127.0.0.1:8086")
    main()
