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
    if origin is None or origin == GATEWAY:
        correlation_id = response_headers.get("X-Correlation-Id")
        assert correlation_id and str(uuid.UUID(correlation_id)) == correlation_id, path
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


INVOICE_LISTS = ("invoices", "invoices/all", "invoices/collectible", "invoices/collectible/all")


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
        for path in INVOICE_LISTS:
            exchange(path, token=member, tenant=company, status=403)
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
        VALUES ('{company_uuid}', '{shift}', 'CASH', '{account}'),
               ('{company_uuid}', '{shift}', 'TRANSFER', '{account}');
        COMMIT;
    """)

    def get(path, tenant=company):
        return exchange(path, token=token, tenant=tenant)

    invoice_path = f"invoices/{invoice['id']}"
    path = f"{invoice_path}/payments"
    number = urlencode({"search": invoice["number"].lower()})
    customer = urlencode({"search": client.lower()})

    def assert_state(invoice_status, sales_status, outstanding, collected, collectible):
        document = get(invoice_path)
        assert document["status"] == "ISSUED"
        query = f"{number}&status={invoice_status}"
        invoice_rows = get(f"invoices/all?{query}")
        assert invoice_rows == get(f"invoices?{query}")["content"] == [document]
        by_customer = f"{customer}&status={invoice_status}"
        assert get(f"invoices/all?{by_customer}") == get(f"invoices?{by_customer}")["content"] == [document]
        for status in {"ISSUED", "PARTIALLY_PAID", "PAID"} - {invoice_status}:
            assert get(f"invoices?{number}&status={status}")["totalElements"] == 0
        listed_sale = next(row for row in get("sales")["content"] if row["id"] == sale)
        for row, status in ((document, invoice_status), (get(f"sales/{sale}"), sales_status),
                            (listed_sale, sales_status)):
            assert row["paymentStatus"] == status
            assert Decimal(row["outstandingBalance"]) == outstanding
            assert Decimal(row["collectedAmount"]) == collected
        receivable = get(f"receivables/{sale}")
        assert receivable["status"] == sales_status
        assert Decimal(receivable["outstandingBalance"]) == outstanding
        assert Decimal(receivable["collectedAmount"]) == collected
        cash_rows = get(f"invoices/collectible/all?{number}")
        assert cash_rows == get(f"invoices/collectible?{number}")["content"]
        assert cash_rows == ([document] if collectible else [])
        assert get(f"invoices/collectible/all?{customer}") == get(
            f"invoices/collectible?{customer}")["content"] == cash_rows

    total = Decimal(invoice["total"])
    assert_state("ISSUED", "OPEN", total, Decimal(0), True)
    key = uuid.uuid4().hex
    data = {"clientName": client, "balance": invoice["total"],
            "lines": [{"amount": "1.00", "method": "CASH"}]}
    payment = exchange(path, token=token, tenant=company, data=data, key=key, status=201)
    assert exchange(path, token=token, tenant=company, data=data, key=key, status=201) == payment
    assert payment["shiftId"] == str(shift) and payment["operatorId"] == str(owner)
    balance = Decimal(str(invoice["total"])) - Decimal("1.00")
    assert Decimal(str(payment["balance"])) == balance
    assert_state("PARTIALLY_PAID", "PARTIALLY_PAID", balance, Decimal("1.00"), True)
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
    assert Decimal(str(summary["totalOtherMethods"])) == 0
    exchange("cash-sessions/active/summary", token=token, tenant=other, status=404)

    remainder = {**data, "balance": str(balance),
                 "lines": [{"amount": str(balance), "method": "TRANSFER", "reference": "QA-TRANSFER"}]}
    start = threading.Barrier(2)

    def finish(_):
        key = uuid.uuid4().hex
        start.wait(timeout=10)
        try:
            return exchange(path, token=token, tenant=company, data=remainder, key=key, status=201)
        except AssertionError as unexpected:
            assert "got 409 (INVOICE_PAYMENT_CONFLICT)" in str(unexpected), unexpected
            return None

    with ThreadPoolExecutor(max_workers=2) as executor:
        settled = [entry for entry in executor.map(finish, range(2)) if entry is not None]
    assert len(settled) == 1 and Decimal(str(settled[0]["balance"])) == 0
    assert_state("PAID", "PAID", Decimal(0), total, False)
    assert get("receivables/all?status=PARTIALLY_PAID") == []
    assert {line["paymentId"] for line in get("cash-sessions/active/payments")} == {
        payment["paymentId"], settled[0]["paymentId"]}

    deadline = time.monotonic() + 30
    while True:
        summary = get("cash-sessions/active/summary")
        if Decimal(str(summary["totalCollections"])) == total:
            break
        assert time.monotonic() < deadline, f"Transfer did not reach Finance: {summary}"
        time.sleep(0.5)
    assert Decimal(str(summary["netCash"])) == Decimal("1.00")
    assert Decimal(str(summary["otherMethods"]["TRANSFER"])) == balance
    assert Decimal(str(summary["totalOtherMethods"])) == balance


def verify_invoice_list_limit(compose, token, company, other, source_invoice):
    source = uuid.UUID(source_invoice["id"])
    # Bulk fixtures exercise the real PostgreSQL query without 1,001 invoice issuance requests.
    fixture(compose, "sanigest_sales", f"""
        BEGIN;
        CREATE TEMP TABLE invoice_list_fixture ON COMMIT DROP AS
        SELECT gen_random_uuid() AS invoice_id, gen_random_uuid() AS sale_id,
               'FAC-' || substring(i.number FROM 5 FOR 4) || '-' || (910000 + n) AS number
        FROM sales.internal_invoices i CROSS JOIN generate_series(0, 1000) n
        WHERE i.id = '{source}';

        INSERT INTO sales.sales
        SELECT (jsonb_populate_record(s, jsonb_build_object('id', f.sale_id, 'code', NULL))).*
        FROM invoice_list_fixture f CROSS JOIN sales.sales s
        JOIN sales.internal_invoices i ON i.sale_id = s.id WHERE i.id = '{source}';

        INSERT INTO sales.internal_invoices
        SELECT (jsonb_populate_record(i, jsonb_build_object(
            'id', f.invoice_id, 'sale_id', f.sale_id, 'number', f.number,
            'idempotency_key', NULL,
            'response_body', (i.response_body::jsonb || jsonb_build_object(
                'id', f.invoice_id, 'saleId', f.sale_id, 'number', f.number))::text))).*
        FROM invoice_list_fixture f CROSS JOIN sales.internal_invoices i WHERE i.id = '{source}';

        INSERT INTO sales.receivables
        SELECT (jsonb_populate_record(r, jsonb_build_object(
            'id', gen_random_uuid(), 'sale_id', f.sale_id, 'balance', r.total_amount, 'status', 'OPEN'))).*
        FROM invoice_list_fixture f CROSS JOIN sales.receivables r
        JOIN sales.internal_invoices i ON i.sale_id = r.sale_id WHERE i.id = '{source}';
        COMMIT;
    """)
    for path in ("invoices", "invoices/collectible"):
        def get(suffix, **kwargs):
            return exchange(f"{path}{suffix}", token=token, tenant=company, **kwargs)

        # All fixture rows share the same date. UUID order must break the tie across pages.
        search = urlencode({"search": "-910"})
        rows = get(f"/all?{search}")
        assert len(rows) == 1000
        assert [row["id"] for row in rows] == sorted((row["id"] for row in rows), reverse=True)
        page0 = get(f"?{search}&size=10")
        page1 = get(f"?{search}&size=10&page=1")
        assert page0["totalElements"] == 1000 and page0["totalPages"] == 100
        assert page0["content"] + page1["content"] == rows[:20]

        search = urlencode({"search": "-91"})
        assert get(f"?{search}&size=1")["totalElements"] == 1001
        problem = get(f"/all?{search}", status=422)
        assert problem["errorCode"] == "INVOICE_LIST_LIMIT_EXCEEDED"
        assert exchange(f"{path}/all?{search}", token=token, tenant=other) == []


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
                 "sales", *INVOICE_LISTS, "receivables", "expenses", "payables", "warehouses",
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
    exchange("catalog-items", token=token, tenant=a, status=400,
             data={"name": "Sin unidad", "type": "PRODUCT", "price": "25.00"})
    client_b = exchange("clients", token=token, tenant=b, data={"name": "Cliente B", "clientType": "LEGAL"}, status=201)["id"]
    item_a = exchange("catalog-items", token=token, tenant=a, status=201, data={
        "name": "Artículo A", "type": "PRODUCT", "price": "25.00", "unitCode": "UNIT",
        "taxClassificationCode": "ITBIS_18", "availableForPurchase": True,
    })["id"]

    supplier = exchange("suppliers", token=token, tenant=a, status=201, data={
        **supplier_request(token, a, item_a, "Proveedor A", "012345678"), "supplyType": "PRODUCT",
    })
    assert supplier["paymentTerms"] == "CASH"
    exchange(f"suppliers/{supplier['id']}/items", token=token, tenant=a, method="PUT", status=204,
             data={"items": [{"catalogItemId": item_a, "offeredCost": "15.0000", "primary": True}]})
    product = exchange(f"catalog-items/{item_a}", token=token, tenant=a)
    assert exchange("suppliers/options?search=Proveedor", token=token, tenant=a) == [
        {"id": supplier["id"], "name": supplier["name"]}]
    assert exchange("suppliers/options?search=Proveedor", token=token, tenant=b) == []
    assert product["primarySupplierName"] == supplier["name"] and product["unitDivisible"] is False

    company_options = exchange(f"companies/{a}/options", token=token, tenant=a)
    assert [option["value"] for option in company_options["invitationRoles"]] == ["OPERATOR", "VIEWER"]
    catalog_options = exchange("catalog/options", token=token, tenant=a)
    assert catalog_options["clientPaymentTerms"] == [
        {"value": "CASH", "label": "Contado"},
        {"value": "CREDIT", "label": "Crédito"},
    ]
    assert catalog_options["supplierSupplyTypes"] == [
        {"value": "PRODUCT", "label": "Productos"},
        {"value": "SERVICE", "label": "Servicios"},
        {"value": "BOTH", "label": "Ambos"},
    ]
    assert catalog_options["supplierSupplyClassifications"] == [
        {"value": "REGULAR", "label": "Regular"},
        {"value": "BOTH", "label": "Productos y servicios"},
        {"value": "PRODUCT", "label": "Productos"},
        {"value": "SERVICE", "label": "Servicios"},
    ]
    assert catalog_options["itemAvailabilityFilters"] == [
        {"value": "ALL", "label": "Todas"},
        {"value": "AVAILABLE", "label": "Disponibles"},
        {"value": "LOW", "label": "Stock bajo"},
        {"value": "OUT", "label": "Agotado"},
    ]
    assert catalog_options["supplierCreditDays"][0] == {"value": None, "label": "No aplica"}
    assert exchange("clients/options?search=Cliente", token=token, tenant=a) == [
        {"id": client_a, "name": client["name"]}]
    assert exchange("clients/options?search=Cliente", token=token, tenant=b) == [
        {"id": client_b, "name": "Cliente B"}]
    assert exchange("suppliers/options?supplyType=SERVICE", token=token, tenant=a) == []
    assert exchange("catalog-items/options?operation=PURCHASE", token=token, tenant=a) == [
        {"id": item_a, "name": product["name"]}]
    sale_selector = urlencode({"operation": "SALE", "search": "Artículo"})
    assert exchange(f"catalog-items/options?{sale_selector}", token=token, tenant=a) == [
        {"id": item_a, "name": product["name"]}]
    assert exchange("catalog-items/options?operation=PURCHASE", token=token, tenant=b) == []
    assert exchange("catalog-items?availability=ALL", token=token, tenant=a)["totalElements"] == 1
    sales_options = exchange("sales/options", token=token, tenant=a)
    assert sales_options["saleModes"] == [
        {"value": "CASH", "label": "Contado"},
        {"value": "CREDIT", "label": "Crédito"},
    ]
    assert sales_options["requestedReceiptTypes"] == [
        {"value": "CONSUMPTION", "label": "Consumidor final"},
        {"value": "FISCAL_CREDIT", "label": "Crédito fiscal"},
    ]
    assert sales_options["invoiceFilters"] == [
        {"value": "ALL", "label": "Todos"},
        {"value": "ISSUED", "label": "Emitida"},
        {"value": "PARTIALLY_PAID", "label": "Parcialmente pagada"},
        {"value": "PAID", "label": "Pagada"},
    ]
    assert sales_options["receivableFilters"] == [
        {"value": "ALL", "label": "Todos"},
        {"value": "PENDING", "label": "Pendiente"},
        {"value": "PARTIALLY_PAID", "label": "Parcial"},
    ]
    assert sales_options["paymentMethods"] == [
        {"value": "CASH", "label": "Efectivo"},
        {"value": "CREDIT_CARD", "label": "Tarjeta de crédito"},
        {"value": "DEBIT_CARD", "label": "Tarjeta de débito"},
        {"value": "TRANSFER", "label": "Transferencia"},
        {"value": "CHECK", "label": "Cheque"},
    ]
    assert sales_options["saleModeFilters"] == [
        {"value": "ALL", "label": "Todas"},
        {"value": "CASH", "label": "Contado"},
        {"value": "CREDIT", "label": "Crédito"},
    ]
    assert sales_options["invoiceTaxRates"] == [
        {"value": 0.18, "label": "18%"},
        {"value": 0.16, "label": "16%"},
        {"value": 0, "label": "0%/Exento"},
    ]

    barrier = threading.Barrier(2)

    def query(tenant):
        barrier.wait(timeout=5)
        return {row["id"] for row in exchange("clients", token=token, tenant=tenant)["content"]}

    with ThreadPoolExecutor(max_workers=2) as pool:
        first, second = pool.submit(query, a), pool.submit(query, b)
        assert first.result(timeout=15) == {client_a}
        assert second.result(timeout=15) == {client_b}

    rates = [option["value"] for option in sales_options["invoiceTaxRates"]]
    line = {"catalogItemId": item_a, "quantity": 1, "unitPrice": 100}
    sale = {"clientId": client_a,
            "lines": [
                {**line, "quantity": 2, "discount": 10, "taxRate": rates[0]},
                {**line, "taxRate": rates[1]},
                {**line, "taxRate": rates[2]},
                line,
            ],
            "mode": "CREDIT", "requestedReceiptType": "CONSUMPTION",
            "dueDate": (business_date() + timedelta(days=30)).isoformat()}
    exchange("invoices", token=token, tenant=a, data={**sale, "mode": "ALL"},
             key=uuid.uuid4().hex, status=400)
    exchange("invoices", token=token, tenant=a, data={**sale, "requestedReceiptType": "ALL"},
             key=uuid.uuid4().hex, status=400)
    for invalid in (
        {"catalogItemId": item_a, "quantity": 1},
        {**line, "unitPrice": None},
        {**line, "taxRate": 0.17},
    ):
        exchange("invoices", token=token, tenant=a, data={**sale, "lines": [invalid]},
                 key=uuid.uuid4().hex, status=400)
    invoice_key = uuid.uuid4().hex
    direct = exchange("invoices", token=token, tenant=a, data=sale, key=invoice_key, status=201)
    sale_id = direct["saleId"]
    assert exchange("invoices", token=token, tenant=a, data=sale, key=invoice_key, status=201) == direct
    changed_sale = {**sale, "dueDate": (business_date() + timedelta(days=31)).isoformat()}
    conflict = exchange("invoices", token=token, tenant=a, data=changed_sale,
                        key=invoice_key, status=409)
    assert conflict["errorCode"] == "IDEMPOTENCY_CONFLICT"
    detail = exchange(f"sales/{sale_id}", token=token, tenant=a)
    assert detail["invoiceNumber"] == direct["number"] and detail["paymentStatus"] == "OPEN"
    receivable = exchange(f"receivables/{sale_id}", token=token, tenant=a)
    assert receivable["invoiceId"] == direct["id"] and receivable["outstandingBalance"] == direct["total"]
    exchange("invoices", token=token, tenant=a, data={**sale, "clientId": client_b},
             key=uuid.uuid4().hex, status=404)
    exchange("sales", token=token, tenant=a, data=sale, key=uuid.uuid4().hex, status=405)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    for mode, count in (("ALL", 1), ("CASH", 0), ("CREDIT", 1)):
        assert exchange(f"sales?mode={mode}", token=token, tenant=a)["totalElements"] == count
    for status in ("ALL", "ISSUED", "PARTIALLY_PAID", "PAID"):
        exchange(f"invoices?status={status}", token=token, tenant=a)
    for status in ("ALL", "PENDING", "PARTIALLY_PAID"):
        exchange(f"receivables?status={status}", token=token, tenant=a)
    direct_path = f"invoices/{direct['id']}"
    assert exchange("invoices", token=token, tenant=a)["totalElements"] == 1
    assert direct["total"] == "540.20"
    assert [(row["subtotal"], row["taxAmount"], row["discount"]) for row in direct["lines"]] == [
        ("190.00", "34.20", "10.00"),
        ("100.00", "16.00", "0.00"),
        ("100.00", "0.00", "0.00"),
        ("100.00", "0.00", "0.00"),
    ]
    exchange(f"catalog-items/{item_a}", token=token, tenant=a, method="PATCH",
             data={"price": "30.00", "taxClassificationCode": "EXEMPT"})
    historical = exchange(direct_path, token=token, tenant=a)
    assert historical["number"] == direct["number"]
    assert historical["total"] == direct["total"] and historical["lines"] == direct["lines"]
    exchange(f"catalog-items/{item_a}", token=token, tenant=a, method="PATCH",
             data={"price": "25.00", "taxClassificationCode": "ITBIS_18"})
    exchange(direct_path, token=token, tenant=b, status=404)
    listed = exchange("invoices/collectible/all", token=token, tenant=a)
    assert direct["id"] in {row["id"] for row in listed}
    cancelled = exchange(f"{direct_path}/cancel", token=token, tenant=a,
                         data={"reason": "Smoke multiempresa"})
    assert cancelled["status"] == "CANCELLED" and cancelled["total"] == direct["total"]
    assert cancelled["paymentStatus"] == "CANCELLED"
    assert cancelled["outstandingBalance"] == cancelled["collectedAmount"] == "0.00"
    assert exchange("invoices/collectible/all", token=token, tenant=a) == []
    assert exchange(f"receivables/{direct['saleId']}", token=token, tenant=a)["outstandingBalance"] == "0.00"
    cancelled_query = urlencode({"search": direct["number"], "status": "ALL"})
    assert exchange(f"invoices/all?{cancelled_query}", token=token, tenant=a) == [cancelled]
    assert exchange(f"invoices?{cancelled_query}", token=token, tenant=b)["totalElements"] == 0
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
    payable = verify_expenses(token, a, b, item_a, warehouse)
    exchange("invoices", token=token, tenant=a, data=sale, key=uuid.uuid4().hex, status=422)
    assert exchange("sales", token=token, tenant=a)["totalElements"] == 1
    if compose:
        verify_roles(compose, token, a, b)
        service = exchange("catalog-items", token=token, tenant=a, status=201,
                           data={"name": "Servicio de cobro", "type": "SERVICE", "price": "25.00",
                                 "unitCode": "UNIT", "taxClassificationCode": "EXEMPT"})
        payable_invoice = exchange("invoices", token=token, tenant=a,
                                   data={**sale, "lines": [{"catalogItemId": service["id"], "quantity": 1, "unitPrice": 25}]},
                                   key=uuid.uuid4().hex, status=201)
        verify_payment(compose, token, owner, a, b, payable_invoice,
                       payable_invoice["saleId"], client["name"])
        verify_payable_payment(compose, token, owner, a, b, payable)
        zero_payload = {**sale, "mode": "CASH", "lines": [
            {"catalogItemId": service["id"], "quantity": 1, "unitPrice": 25, "discount": "25.00"}]}
        del zero_payload["dueDate"]
        zero = exchange("invoices", token=token, tenant=a, data=zero_payload,
                        key=uuid.uuid4().hex, status=201)
        zero_detail = exchange(f"invoices/{zero['id']}", token=token, tenant=a)
        assert zero_detail["status"] == "ISSUED" and zero_detail["paymentStatus"] == "PAID"
        assert zero_detail["total"] == zero_detail["outstandingBalance"] == zero_detail["collectedAmount"] == "0.00"
        zero_sale = exchange(f"sales/{zero['saleId']}", token=token, tenant=a)
        assert zero_sale["paymentStatus"] == "PAID" and zero_sale["outstandingBalance"] == "0.00"
        assert exchange("invoices/collectible/all", token=token, tenant=a) == []
        verify_invoice_list_limit(compose, token, a, b, payable_invoice)
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
    query = urlencode({"search": "Artículo A", "size": 1})
    global_stock = inventory(f"inventory/stock?{query}")
    assert global_stock["content"] == batch["content"]
    assert global_stock["totalElements"] == global_stock["totalPages"] == 1
    assert inventory(f"inventory/stock?{query}&page=1")["content"] == []
    invalid_batch = inventory(f"inventory/stock?catalogItemIds={item}", status=400)
    assert invalid_batch["errorCode"] == "INVALID_STOCK_BATCH"
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


def supplier_request(token, company, item, name, tax_id):
    categories = exchange("suppliers/categories", token=token, tenant=company)
    return {
        "name": name, "taxId": tax_id, "phone": "8095550118", "contact": "Compras",
        "email": "supplier@example.do", "supplyType": "REGULAR", "categoryId": categories[0]["id"],
        "creditDays": None, "status": "ACTIVE",
        "items": [{"catalogItemId": item, "offeredCost": "10.00", "supplierReference": "SUP-1"}],
    }


def verify_expenses(token, company, other, item, warehouse):
    options = exchange("expenses/options", token=token, tenant=company)
    assert options["operationTypes"] == [
        {"value": "PURCHASE", "label": "Compra de mercancía o suministro"},
        {"value": "OPERATING_EXPENSE", "label": "Gasto operativo"},
        {"value": "ASSET_ACQUISITION", "label": "Adquisición de activo"},
    ]
    assert options["receiptTypes"] == [
        {"value": "FISCAL_CREDIT", "label": "Crédito fiscal"},
        {"value": "CONSUMPTION", "label": "Consumo"},
        {"value": "PURCHASE", "label": "Compra"},
        {"value": "MINOR_EXPENSES", "label": "Gastos menores"},
        {"value": "OTHER", "label": "Otro"},
        {"value": "NOT_APPLICABLE", "label": "No aplica"},
    ]
    assert options["modes"] == [
        {"value": "CASH", "label": "Contado"},
        {"value": "CREDIT", "label": "Crédito"},
    ]
    assert options["receiptStatuses"] == [
        {"value": "PENDING", "label": "Pendiente de recepción"},
        {"value": "COMPLETE", "label": "Recibida completamente"},
        {"value": "PARTIAL", "label": "Recibida parcialmente"},
    ]
    assert options["taxClassifications"] == [
        {"value": "ITBIS_18", "label": "18%"},
        {"value": "ITBIS_16", "label": "16%"},
        {"value": "EXEMPT", "label": "0%/Exento"},
    ]
    assert options["operationTypeFilters"] == [
        {"value": "ALL", "label": "Todas"},
        {"value": "PURCHASE", "label": "Compra"},
        {"value": "OPERATING_EXPENSE", "label": "Gasto"},
        {"value": "ASSET_ACQUISITION", "label": "Activo"},
    ]
    assert options["statusFilters"] == [
        {"value": "ALL", "label": "Todos"},
        {"value": "PAID", "label": "Pagado"},
        {"value": "PENDING", "label": "Pendiente"},
        {"value": "PARTIALLY_PAID", "label": "Parcial"},
    ]
    assert options["payableFilters"] == [
        {"value": "ALL", "label": "Todos"},
        {"value": "PENDING", "label": "Pendiente"},
        {"value": "PARTIALLY_PAID", "label": "Parcial"},
    ]
    for option in options["operationTypeFilters"]:
        exchange(f"expenses?operationType={option['value']}", token=token, tenant=company)
    for option in options["statusFilters"]:
        exchange(f"expenses?status={option['value']}", token=token, tenant=company)
    for option in options["payableFilters"]:
        exchange(f"payables?status={option['value']}", token=token, tenant=company)
    supplier_input = {**supplier_request(token, company, item, "Proveedor a crédito", "012345679"),
                      "paymentTerms": "BOTH", "creditDays": 30}
    for field in ("name", "taxId", "phone", "contact", "email", "supplyType",
                  "categoryId", "creditDays", "status", "items"):
        invalid = {key: value for key, value in supplier_input.items() if key != field}
        exchange("suppliers", token=token, tenant=company, data=invalid, status=400)
    for field in ("catalogItemId", "offeredCost", "supplierReference"):
        line = {key: value for key, value in supplier_input["items"][0].items() if key != field}
        exchange("suppliers", token=token, tenant=company,
                 data={**supplier_input, "items": [line]}, status=400)
    foreign = exchange("suppliers/categories", token=token, tenant=other)[0]["id"]
    exchange("suppliers", token=token, tenant=company,
             data={**supplier_input, "categoryId": foreign}, status=400)
    exchange("suppliers", token=token, tenant=company, status=404, data={
        **supplier_input, "items": [{**supplier_input["items"][0], "catalogItemId": str(uuid.uuid4())}],
    })
    supplier = exchange("suppliers", token=token, tenant=company, status=201, data=supplier_input)
    assert exchange(f"suppliers/{supplier['id']}/items", token=token, tenant=company)["totalElements"] == 1
    detail = exchange(f"suppliers/{supplier['id']}", token=token, tenant=company)
    listed = exchange("suppliers?supplyType=REGULAR", token=token, tenant=company)["content"]
    assert [entry["id"] for entry in listed] == [supplier["id"]]
    assert listed[0]["outstandingAmount"] == 0
    for record in (supplier, detail, *listed):
        assert record["supplyType"] == "BOTH" and record["supplyClassification"] == "REGULAR"
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
    for field in ("operationType", "mode", "receiptType", "receiptStatus"):
        exchange("expenses", token=token, tenant=company, data={**request, field: "ALL"},
                 key=uuid.uuid4().hex, status=400)
    inactive = exchange("warehouses", token=token, tenant=company, status=201,
                        data={"code": "ALM-INACTIVO", "name": "Inactivo"})["id"]
    exchange(f"warehouses/{inactive}", token=token, tenant=company, method="PATCH",
             data={"status": "INACTIVE"})
    foreign = exchange("warehouses", token=token, tenant=other, status=201,
                       data={"code": "ALM-OTRO", "name": "Otra empresa"})["id"]
    missing = {field: value for field, value in request.items() if field != "warehouseId"}
    for invalid in (missing, {**request, "warehouseId": None}):
        problem = exchange("expenses", token=token, tenant=company, data=invalid,
                           key=uuid.uuid4().hex, status=400)
        assert "warehouseId" in problem["errors"]
    for invalid_warehouse in (inactive, foreign):
        problem = exchange("expenses", token=token, tenant=company,
                           data={**request, "warehouseId": invalid_warehouse},
                           key=uuid.uuid4().hex, status=400)
        assert problem["errorCode"] == "EXPENSE_INVALID_WAREHOUSE"
    for path in ("expenses", "payables"):
        assert exchange(path, token=token, tenant=company)["totalElements"] == 0
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
    search = urlencode({"search": "proveedor a crédito", "size": 1})
    supplier_row = exchange(f"suppliers?{search}", token=token, tenant=company)
    assert supplier_row["totalElements"] == 1
    assert supplier_row["content"][0]["outstandingAmount"] == 29.5
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
    pending_document = exchange("expenses", token=token, tenant=company, key=uuid.uuid4().hex,
                                status=201, data={**request, "receiptStatus": "PENDING",
                                "supplierInvoiceNumber": "COMPRA-ANULAR",
                                "lines": [{**request["lines"][0], "receivedQuantity": "0"}]})
    reason = {"reason": "Registro duplicado"}
    historic_path = f"expenses/{pending_document['id']}"
    cancel_path = f"{historic_path}/cancel"
    assert exchange("payables", token=token, tenant=company)["totalElements"] == 2
    assert exchange("expenses/summary", token=token, tenant=company)["operations"] == 3
    exchange(cancel_path, token=token, tenant=other, data=reason, status=404)
    exchange(cancel_path, token=token, tenant=company, data={"reason": " "}, status=400)
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(lambda body: exchange(cancel_path, token=token, tenant=company,
                                                 data=body, status=204), (reason, reason)))
    exchange(cancel_path, token=token, tenant=company, data={"reason": "Motivo nuevo"}, status=204)
    historic = exchange(historic_path, token=token, tenant=company)
    assert historic["status"] == "CANCELLED" and historic["outstandingAmount"] == 0
    assert historic["cancellationReason"] == reason["reason"] and historic["cancelledAt"]
    assert historic["lines"] == pending_document["lines"] and historic["attachmentIds"] == [attachment]
    assert exchange("expenses?search=COMPRA-ANULAR", token=token, tenant=company)["totalElements"] == 0
    assert exchange("expenses/all?search=COMPRA-ANULAR", token=token, tenant=company) == []
    assert exchange("payables", token=token, tenant=company)["totalElements"] == 1
    assert exchange("expenses/summary", token=token, tenant=company)["operations"] == 2
    payment = exchange(f"payables/{pending_document['id']}/payments", token=token, tenant=company,
                       key=uuid.uuid4().hex, status=409,
                       data={"amount": "1.00", "method": "TRANSFER", "accountId": str(uuid.uuid4()),
                             "date": business_date().isoformat(), "reference": "TRF-ANULADA"})
    assert payment["errorCode"] == "PAYABLE_CANCELLED"
    audit = exchange("expenses/audit-log?operation=EXPENSE_CANCELLED", token=token, tenant=company)
    assert [entry["resourceId"] for entry in audit["content"]] == [pending_document["id"]]
    assert exchange(f"{detail_path}/cancel", token=token, tenant=company, data=reason,
                    status=409)["errorCode"] == "EXPENSE_HAS_RECEIPT"
    filters = urlencode({"search": "COMPRA-001", "operationType": "PURCHASE"})
    page = exchange(f"expenses?{filters}&size=1", token=token, tenant=company)
    assert page["totalElements"] == 1 and page["content"][0]["id"] == created["id"]
    assert exchange(f"expenses/all?{filters}", token=token, tenant=company) == page["content"]
    assert exchange(f"expenses/all?{filters}", token=token, tenant=other) == []
    stock = exchange(f"inventory/stock/{item}/{warehouse}", token=token, tenant=company)
    assert stock["onHand"] == "24.000"
    movements = exchange(f"inventory/movements?warehouseId={warehouse}", token=token, tenant=company)
    assert movements["totalElements"] == 4
    return created["id"]


def verify_payable_payment(compose, token, owner, company, other, payable):
    shift, account = uuid.uuid4(), str(uuid.uuid4())
    fixture(compose, "sanigest_finance", f"""
        BEGIN;
        INSERT INTO finance.cash_shifts (company_id, id, operator_id, opened_at, closed_at)
        VALUES ('{uuid.UUID(company)}', '{shift}', '{owner}', now(), now());
        INSERT INTO finance.cash_accounts (company_id, id) VALUES ('{uuid.UUID(company)}', '{account}');
        INSERT INTO finance.shift_payment_accounts (company_id, shift_id, method, account_id)
        VALUES ('{uuid.UUID(company)}', '{shift}', 'TRANSFER', '{account}');
        COMMIT;
    """)
    path = f"payables/{payable}/payments"
    request = {"amount": "10.00", "method": "TRANSFER", "accountId": account,
               "date": business_date().isoformat(), "reference": "TRF-001", "notes": "Abono"}
    key = uuid.uuid4().hex

    def pay(data=request, request_key=key, status=201, tenant=company):
        return exchange(path, token=token, tenant=tenant, data=data, key=request_key, status=status)

    exchange(path, tenant=company, data=request, key=key, status=401)
    pay(tenant=other, status=404)
    pay(request_key=None, status=400)
    pay({**request, "amount": "0.00"}, status=400)
    assert pay({**request, "reference": None}, status=400)["errorCode"] == (
        "PAYABLE_INVALID_PAYMENT_REFERENCE")
    assert pay({**request, "method": "CHECK"}, status=400)["errorCode"] == (
        "PAYABLE_INELIGIBLE_PAYMENT_ACCOUNT")
    fixture(compose, "sanigest_expense", f"""
        UPDATE expense.payables SET cancelled_at = now() WHERE expense_id = '{uuid.UUID(payable)}';
    """)
    assert pay(status=409)["errorCode"] == "PAYABLE_CANCELLED"
    fixture(compose, "sanigest_expense", f"""
        UPDATE expense.payables SET cancelled_at = NULL WHERE expense_id = '{uuid.UUID(payable)}';
    """)
    with ThreadPoolExecutor(max_workers=2) as executor:
        first, replay = list(executor.map(lambda _: pay(), range(2)))
    assert first == replay and first["status"] == "PARTIALLY_PAID"
    assert exchange(f"expenses/{payable}/cancel", token=token, tenant=company,
                    data={"reason": "Con pagos"}, status=409)["errorCode"] == "EXPENSE_HAS_PAYMENTS"
    assert pay({**request, "amount": "10.0", "notes": " Abono "}) == first
    pay(tenant=other, status=404)
    assert Decimal(str(first["outstandingAmount"])) == Decimal("19.50")
    assert pay({**request, "amount": "11.00"}, status=409)["errorCode"] == (
        "PAYABLE_PAYMENT_IDEMPOTENCY_CONFLICT")

    def finish(index):
        # Both requests attempt to settle the full remaining balance.
        try:
            return exchange(path, token=token, tenant=company, data={**request, "amount": "19.50"},
                            key=keys[index], status=201)
        except AssertionError as unexpected:
            assert "(PAYABLE_PAYMENT_EXCEEDS_BALANCE)" in str(unexpected), unexpected
            return None

    keys = [uuid.uuid4().hex, uuid.uuid4().hex]
    with ThreadPoolExecutor(max_workers=2) as executor:
        settled = [payment for payment in executor.map(finish, range(2)) if payment]
    assert len(settled) == 1 and settled[0]["status"] == "PAID"
    assert settled[0]["outstandingAmount"] == 0 and pay() == first
    supplier = exchange("suppliers?supplyType=REGULAR", token=token, tenant=company)["content"][0]
    assert supplier["outstandingAmount"] == 0
    exchange(f"suppliers/{supplier['id']}/status", token=token, tenant=company,
             method="PATCH", data={"status": "INACTIVE"})
    assert exchange("suppliers/options?supplyType=REGULAR", token=token, tenant=company) == []


if __name__ == "__main__":
    GATEWAY = os.environ.get("SMOKE_GATEWAY_URL", "http://127.0.0.1:8085")
    CATALOG = os.environ.get("SMOKE_CATALOG_URL", "http://127.0.0.1:8083")
    INVENTORY = os.environ.get("SMOKE_INVENTORY_URL", "http://127.0.0.1:8086")
    EXPENSE = os.environ.get("SMOKE_EXPENSE_URL", "http://127.0.0.1:8087")
    main(sys.argv[1:])
