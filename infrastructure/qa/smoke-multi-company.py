"""Run against the disposable local multi-company Compose stack."""
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from decimal import Decimal
from urllib.parse import urlencode
from zoneinfo import ZoneInfo


def exchange(path, *, token=None, tenant=None, data=None, key=None, status=200,
             origin=None, method=None, content_type="application/json", problem=True):
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
        assert decoded["status"] == status, path
    if status >= 400 and problem:
        assert response_headers.get_content_type() == "application/problem+json", path
        required = ("type", "title", "detail", "errorCode", "timestamp", "traceId")
        missing = [field for field in required if not decoded.get(field)]
        assert not missing, f"HTTP {status}: missing problem fields {missing}"
    if status == 204:
        assert not payload
    return decoded


def fixture(compose, database, sql):
    subprocess.run(
        [*compose, "exec", "-T", "postgres", "sh", "-c",
         'exec psql -Xq -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"', "psql", database],
        input=sql, text=True, check=True, timeout=30,
    )


def business_date():
    return datetime.now(ZoneInfo("America/Santo_Domingo")).date()


def verify_roles(compose, owner, company, other):
    audit = f"companies/{company}/audit-log"
    for role in ("ADMIN", "OPERATOR", "VIEWER"):
        credentials = {"email": f"{role.lower()}-{uuid.uuid4().hex}@example.test",
                       "password": "Disposable-smoke-454!"}
        user = uuid.UUID(exchange("auth/register", data={**credentials, "name": role},
                                  status=201)["userId"])
        member = exchange("auth/login", data=credentials)["accessToken"]
        fixture(compose, "sanigest_company", f"""
            INSERT INTO company_memberships (id, company_id, user_id, role)
            VALUES ('{uuid.uuid4()}', '{uuid.UUID(company)}', '{user}', '{role}');
        """)
        assert exchange(f"companies/{company}", token=member)["companyId"] == company
        exchange(f"companies/{other}", token=member, status=403)
        exchange(audit, token=member, status=403)
        if role == "ADMIN":
            exchange(f"companies/{company}", token=member, method="PATCH",
                     data={"phone": "8095550101"})
        elif role == "VIEWER":
            exchange("clients", token=member, tenant=company)
            exchange("clients", token=member, tenant=company,
                     data={"name": "Forbidden", "clientType": "NATURAL"}, status=403)
        else:
            exchange("sales", token=member, tenant=company)
            exchange(f"companies/{company}", token=member, method="PATCH",
                     data={"phone": "8095550102"}, status=403)

        exchange(f"companies/{company}/members/{user}/status", token=owner, method="PATCH",
                 data={"status": "SUSPENDED"})
        exchange(f"companies/{company}", token=member, status=403)
        entries = exchange(f"{audit}?size=20", token=owner)["content"]
        assert any(entry["companyId"] == company and entry["targetUserId"] == str(user)
                   and entry["newStatus"] == "SUSPENDED" for entry in entries), entries
        assert all(entry["targetUserId"] != str(user)
                   for entry in exchange(f"companies/{other}/audit-log", token=owner)["content"])


def verify_payment(compose, token, owner, company, other, invoice, sale, client):
    company_uuid = uuid.UUID(company)
    shift, account = uuid.uuid4(), uuid.uuid4()
    fixture(compose, "sanigest_finance", f"""
        BEGIN;
        INSERT INTO finance.cash_shifts (company_id, id, operator_id, opened_at)
        VALUES ('{company_uuid}', '{shift}', '{owner}', now());
        INSERT INTO finance.cash_accounts (company_id, id) VALUES ('{company_uuid}', '{account}');
        INSERT INTO finance.shift_payment_accounts (company_id, shift_id, method, account_id)
        VALUES ('{company_uuid}', '{shift}', 'CASH', '{account}');
        COMMIT;
    """)

    def get(path, tenant=company):
        return exchange(path, token=token, tenant=tenant)

    path = f"invoices/{invoice['id']}/payments"
    key = uuid.uuid4().hex
    data = {"clientName": client, "balance": invoice["total"],
            "lines": [{"amount": "1.00", "method": "CASH"}]}
    payment = exchange(path, token=token, tenant=company, data=data, key=key, status=201)
    assert exchange(path, token=token, tenant=company, data=data, key=key, status=201) == payment
    assert payment["shiftId"] == str(shift) and payment["operatorId"] == str(owner)
    balance = Decimal(str(invoice["total"])) - Decimal("1.00")
    assert Decimal(str(payment["balance"])) == balance
    assert Decimal(str(get(f"receivables/{sale}")["outstandingBalance"])) == balance
    page = get("receivables?status=PARTIALLY_PAID&size=1")
    assert page["totalElements"] == 1 and page["content"] == get("receivables/all?status=PARTIALLY_PAID")
    assert get("receivables/all?status=PENDING") == []
    assert get("receivables/all?status=PARTIALLY_PAID", other) == []

    history = get("cash-sessions/active/payments")
    assert [line["paymentId"] for line in history] == [payment["paymentId"]]
    assert get("cash-sessions/active/payments", other) == []

    deadline = time.monotonic() + 30
    while True:
        summary = get("cash-sessions/active/summary")
        if Decimal(str(summary["totalCollections"])) == Decimal("1.00"):
            break
        assert time.monotonic() < deadline, f"Payment did not reach Finance: {summary}"
        time.sleep(0.5)
    assert summary["shiftId"] == str(shift) and Decimal(str(summary["netCash"])) == Decimal("1.00")
    exchange("cash-sessions/active/summary", token=token, tenant=other, status=404)


def main(compose):
    suffix = uuid.uuid4().hex
    credentials = {"email": f"tenant-{suffix}@example.test", "password": "Disposable-smoke-454!"}
    owner = uuid.UUID(exchange("auth/register", data={**credentials, "name": "Multiempresa Smoke"},
                               status=201)["userId"])
    session = exchange("auth/login", data=credentials)
    session = exchange("auth/refresh", data={"refreshToken": session["refreshToken"]})
    token = session["accessToken"]
    assert exchange("auth/me", token=token)["id"] == str(owner)
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
    for path in ("auth/me", "companies", "advisor/companies", "clients", "catalog-items",
                 "sales", "invoices", "receivables", "expenses", "payables", "warehouses",
                 "inventory/stock", "cash-sessions/active/summary",
                 "cash-sessions/active/payments", "reports/projection"):
        exchange(path, tenant=a, status=401)
    exchange("reports/projection/rebuild", tenant=a, method="POST", status=401)
    for retired_path in ("movements", "reports/summary"):
        exchange(retired_path, token=token, tenant=a, status=404)
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

    company_options = exchange(f"companies/{a}/options", token=token, tenant=a)
    assert [option["value"] for option in company_options["invitationRoles"]] == ["OPERATOR", "VIEWER"]
    catalog_options = exchange("catalog/options", token=token, tenant=a)
    assert catalog_options["supplierCreditDays"][0] == {"value": None, "label": "No aplica"}
    assert exchange("clients/options?search=Cliente", token=token, tenant=a) == [
        {"id": client_a, "name": client["name"]}]
    assert exchange("clients/options?search=Cliente", token=token, tenant=b) == [
        {"id": client_b, "name": "Cliente B"}]
    assert exchange("suppliers/options?supplyType=SERVICE", token=token, tenant=a) == []
    assert exchange("catalog-items/options?operation=PURCHASE", token=token, tenant=a) == [
        {"id": item_a, "name": product["name"]}]
    sales_options = exchange("sales/options", token=token, tenant=a)
    assert [option["value"] for option in sales_options["receivableFilters"]] == [
        "ALL", "PENDING", "PARTIALLY_PAID"]

    barrier = threading.Barrier(2)

    def query(tenant):
        barrier.wait(timeout=5)
        return {row["id"] for row in exchange("clients", token=token, tenant=tenant)["content"]}

    with ThreadPoolExecutor(max_workers=2) as pool:
        first, second = pool.submit(query, a), pool.submit(query, b)
        assert first.result(timeout=15) == {client_a}
        assert second.result(timeout=15) == {client_b}

    sale = {"clientId": client_a, "lines": [{"catalogItemId": item_a, "quantity": 1}],
            "mode": "CREDIT", "dueDate": (business_date() + timedelta(days=30)).isoformat()}
    invoice_key = uuid.uuid4().hex
    direct = exchange("invoices", token=token, tenant=a, data=sale, key=invoice_key, status=201)
    sale_id = direct["saleId"]
    assert exchange("invoices", token=token, tenant=a, data=sale, key=invoice_key, status=201) == direct
    detail = exchange(f"sales/{sale_id}", token=token, tenant=a)
    assert detail["invoiceNumber"] == direct["number"] and detail["paymentStatus"] == "OPEN"
    receivable = exchange(f"receivables/{sale_id}", token=token, tenant=a)
    assert receivable["invoiceId"] == direct["id"] and receivable["outstandingBalance"] == direct["total"]
    exchange("invoices", token=token, tenant=a, data={**sale, "clientId": client_b},
             key=uuid.uuid4().hex, status=404)
    exchange("sales", token=token, tenant=a, data=sale, key=uuid.uuid4().hex, status=405)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    direct_path = f"invoices/{direct['id']}"
    assert exchange(direct_path, token=token, tenant=a)["number"] == direct["number"]
    exchange(direct_path, token=token, tenant=b, status=404)
    listed = exchange("invoices/collectible/all", token=token, tenant=a)
    assert direct["id"] in {row["id"] for row in listed}
    cancelled = exchange(f"{direct_path}/cancel", token=token, tenant=a,
                         data={"reason": "Smoke multiempresa"})
    assert cancelled["status"] == "CANCELLED" and cancelled["total"] == direct["total"]
    assert exchange(f"receivables/{direct['saleId']}", token=token, tenant=a)["outstandingBalance"] == "0.00"
    for path in (f"sales/{sale_id}", f"receivables/{sale_id}", f"clients/{client_a}"):
        exchange(path, token=token, tenant=b, status=404)
    exchange(f"{direct_path}/payments", token=token, tenant=b, method="POST",
             data={"clientName": client["name"], "balance": direct["total"],
                   "lines": [{"amount": "1.00", "method": "CASH"}]}, key=uuid.uuid4().hex, status=404)
    unauthorized = str(uuid.uuid4())
    exchange("clients", token=token, tenant=unauthorized, status=403)
    exchange("clients", token=token, tenant=unauthorized, origin=CATALOG, status=403)
    exchange("cash-sessions/active/payments", token=token, tenant=unauthorized, status=403)
    for path in ("cash-sessions/active/summary", "reports/projection"):
        denied = exchange(path, token=token, tenant=unauthorized, status=403, problem=False)
        assert {"shiftId", "activity", "receivables", "payables"}.isdisjoint(denied)
    exchange("clients", token=token, status=400)
    exchange("clients", token=token, status=400, origin=CATALOG)
    exchange(f"clients/{client_a}", token=token, tenant=b, status=404, origin=CATALOG)
    warehouse = verify_inventory(token, a, b, item_a)
    verify_expenses(token, a, b, item_a, warehouse)
    exchange("invoices", token=token, tenant=a, data=sale, key=uuid.uuid4().hex, status=422)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    if compose:
        verify_roles(compose, token, a, b)
        service = exchange("catalog-items", token=token, tenant=a, status=201,
                           data={"name": "Servicio de cobro", "type": "SERVICE", "price": "25.00",
                                 "unitCode": "UNIT", "taxClassificationCode": "EXEMPT"})
        payable_invoice = exchange("invoices", token=token, tenant=a,
                                   data={**sale, "lines": [{"catalogItemId": service["id"], "quantity": 1}]},
                                   key=uuid.uuid4().hex, status=201)
        verify_payment(compose, token, owner, a, b, payable_invoice,
                       payable_invoice["saleId"], client["name"])
    refresh = {"refreshToken": session["refreshToken"]}
    exchange("auth/logout", data=refresh, status=204)
    exchange("auth/refresh", data=refresh, status=401)
    print("Multi-company HTTP smoke passed")


def verify_inventory(token, company, other, item):
    def inventory(path, **kwargs):
        return exchange(path, token=token, tenant=company, **kwargs)

    warehouse = inventory("warehouses", data={"code": "ALM-001", "name": "Principal"}, status=201)["id"]
    assert inventory("warehouses/options?search=principal") == [{"id": warehouse, "name": "Principal"}]
    assert exchange("warehouses/options", token=token, tenant=other) == []
    assert inventory("inventory/options")["availabilityFilters"][0] == {"value": "ALL", "label": "Todos"}
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
        for origin in (None, CATALOG):
            exchange(path, token=token, tenant=other, status=404, origin=origin)
    adjust(service, "IN", "1", uuid.uuid4().hex, 400)
    exchange(position, token=token, tenant=other, status=404)
    exchange("inventory/stock", status=401, origin=INVENTORY)
    exchange("inventory/stock", token=token, status=400, origin=INVENTORY)
    assert inventory(f"inventory/movements?warehouseId={warehouse}")["totalElements"] == 3

    return warehouse


def verify_expenses(token, company, other, item, warehouse):
    supplier = exchange("suppliers", token=token, tenant=company, status=201, data={
        "name": "Proveedor a crédito", "contact": "Compras", "supplyType": "PRODUCT",
        "paymentTerms": "BOTH", "creditDays": 30,
    })
    issue_date = business_date()
    attachment = str(uuid.uuid4())
    request = {
        "attachmentId": attachment,
        "operationType": "PURCHASE", "documentDate": issue_date.isoformat(),
        "receiptType": "FISCAL_CREDIT", "mode": "CREDIT", "supplierId": supplier["id"],
        "supplierInvoiceNumber": "COMPRA-001", "warehouseId": warehouse, "receiptStatus": "PARTIAL",
        "lines": [{"itemId": item, "quantity": "3", "unitCost": "10.00",
                   "taxClassification": "ITBIS_18", "discount": "5.00", "receivedQuantity": "2"}],
    }
    key = uuid.uuid4().hex
    exchange("expenses", token=token, data=request, key=key, status=400)
    exchange("expenses", token=token, tenant=other, data=request, key=key, status=400)
    def register():
        return exchange("expenses", token=token, tenant=company, data=request, key=key, status=201)

    with ThreadPoolExecutor(max_workers=2) as executor:
        first = executor.submit(register)
        second = executor.submit(register)
        created, concurrent_replay = first.result(), second.result()
    assert created == concurrent_replay
    assert created["receiptStatus"] == "PARTIAL" and created["financialStatus"] == "OPEN"
    assert created["subtotal"] == 25 and created["tax"] == 4.5 and created["total"] == 29.5
    assert created["dueDate"] == (issue_date + timedelta(days=30)).isoformat()
    assert register() == created
    detail_path = f"expenses/{created['id']}"
    for origin in (None, EXPENSE):
        assert exchange(detail_path, token=token, tenant=company, origin=origin)["attachmentIds"] == [attachment]
        exchange(detail_path, token=token, tenant=other, origin=origin, status=404)
        filtered = f"payables?supplierId={supplier['id']}"
        assert exchange(filtered, token=token, tenant=company, origin=origin)["totalElements"] == 1
        assert exchange(filtered, token=token, tenant=other, origin=origin)["totalElements"] == 0
        exchange("expenses", tenant=company, origin=origin, status=401)
    pending = f"status=PENDING&supplierId={supplier['id']}"
    page = exchange(f"payables?{pending}&size=1", token=token, tenant=company)
    assert page["totalElements"] == 1 and page["content"][0]["outstandingAmount"] == created["total"]
    assert exchange(f"payables/all?{pending}", token=token, tenant=company) == page["content"]
    assert exchange(f"payables/all?{pending}", token=token, tenant=other) == []
    conflict = exchange("expenses", token=token, tenant=company, data={**request, "notes": "Cambiada"},
                        key=key, status=409)
    assert conflict["errorCode"] == "EXPENSE_IDEMPOTENCY_CONFLICT"
    cash = exchange("expenses", token=token, tenant=company, key=uuid.uuid4().hex, status=201,
                    data={**request, "mode": "CASH", "receiptStatus": "PENDING",
                          "supplierInvoiceNumber": "COMPRA-002",
                          "lines": [{**request["lines"][0], "receivedQuantity": "0"}]})
    assert cash["financialStatus"] == "PAYMENT_UNRECORDED" and cash["dueDate"] is None
    filters = urlencode({"search": "COMPRA-001", "operationType": "PURCHASE"})
    page = exchange(f"expenses?{filters}&size=1", token=token, tenant=company)
    assert page["totalElements"] == 1 and page["content"][0]["id"] == created["id"]
    assert exchange(f"expenses/all?{filters}", token=token, tenant=company) == page["content"]
    assert exchange(f"expenses/all?{filters}", token=token, tenant=other) == []
    stock = exchange(f"inventory/stock/{item}/{warehouse}", token=token, tenant=company)
    assert stock["onHand"] == "24.000"
    movements = exchange(f"inventory/movements?warehouseId={warehouse}", token=token, tenant=company)
    assert movements["totalElements"] == 4


if __name__ == "__main__":
    GATEWAY = os.environ.get("SMOKE_GATEWAY_URL", "http://127.0.0.1:8085")
    CATALOG = os.environ.get("SMOKE_CATALOG_URL", "http://127.0.0.1:8083")
    INVENTORY = os.environ.get("SMOKE_INVENTORY_URL", "http://127.0.0.1:8086")
    EXPENSE = os.environ.get("SMOKE_EXPENSE_URL", "http://127.0.0.1:8087")
    main(sys.argv[1:])
