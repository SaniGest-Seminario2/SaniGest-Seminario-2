"""Run against the disposable local multi-company Compose stack."""
import json
import os
import threading
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor


def exchange(path, *, token=None, tenant=None, data=None, key=None, status=200, origin=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if tenant:
        headers["X-Company-Id"] = tenant
    if key:
        headers["Idempotency-Key"] = key
    body = None
    if data is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(data).encode()
    request = urllib.request.Request(f"{origin or GATEWAY}/api/v1/{path}", body, headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            actual, payload = response.status, response.read()
    except urllib.error.HTTPError as response:
        actual, payload = response.code, response.read()
    if actual != status:
        raise AssertionError(f"{request.method} {path}: expected {status}, got {actual}")
    return json.loads(payload) if payload else None


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
    client_a = exchange("clients", token=token, tenant=a, data={"name": "Cliente A"}, status=201)["id"]
    client_b = exchange("clients", token=token, tenant=b, data={"name": "Cliente B"}, status=201)["id"]
    item_a = exchange("catalog-items", token=token, tenant=a,
                      data={"name": "Artículo A", "type": "PRODUCT", "price": 25}, status=201)["id"]

    barrier = threading.Barrier(2)

    def query(tenant):
        barrier.wait(timeout=5)
        return {row["id"] for row in exchange("clients", token=token, tenant=tenant)["content"]}

    with ThreadPoolExecutor(max_workers=2) as pool:
        first, second = pool.submit(query, a), pool.submit(query, b)
        assert first.result(timeout=15) == {client_a}
        assert second.result(timeout=15) == {client_b}

    sale = {"clientId": client_a, "lines": [{"catalogItemId": item_a, "quantity": 1}], "mode": "CASH"}
    sale_id = exchange("sales", token=token, tenant=a, data=sale,
                       key=uuid.uuid4().hex, status=201)["id"]
    exchange("sales", token=token, tenant=a, data={**sale, "clientId": client_b},
             key=uuid.uuid4().hex, status=404)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    exchange(f"sales/{sale_id}", token=token, tenant=b, status=404)
    exchange("clients", token=token, status=400)
    exchange("clients", token=token, status=400, origin=CATALOG)
    exchange(f"clients/{client_a}", token=token, tenant=b, status=404, origin=CATALOG)
    print("Multi-company HTTP smoke passed")


if __name__ == "__main__":
    GATEWAY = os.environ.get("SMOKE_GATEWAY_URL", "http://127.0.0.1:8085")
    CATALOG = os.environ.get("SMOKE_CATALOG_URL", "http://127.0.0.1:8083")
    main()
