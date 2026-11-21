"""Run against disposable local/test deployments of Identity and Company."""
import json
import secrets
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4


def request(base_url, path, expected_status, payload=None, token=None, key=None):
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if key:
        headers["Idempotency-Key"] = key
    try:
        with urlopen(Request(base_url.rstrip("/") + path, body, headers), timeout=10) as response:
            if response.status != expected_status:
                raise SystemExit(f"{path}: HTTP {response.status}, expected {expected_status}")
            return json.load(response)
    except HTTPError as error:
        raise SystemExit(f"{path}: HTTP {error.code}, expected {expected_status}") from None


def main(identity_url, company_url):
    run_id = uuid4().hex
    email = f"company-smoke-{run_id}@example.com"
    login = {"email": email, "password": secrets.token_urlsafe(24)}
    request(identity_url, "/api/v1/auth/register", 201, {**login, "name": "Company smoke"})
    token = request(identity_url, "/api/v1/auth/login", 200, login)["accessToken"]
    data = {
        "companyName": "Company Smoke SRL",
        "businessName": "Company Smoke",
        "location": "Santo Domingo",
    }
    created = request(
        company_url,
        "/api/v1/companies",
        201,
        {**data, "email": email, "phone": "8095555555"},
        token,
        run_id,
    )
    profile = request(company_url, "/api/v1/companies/me", 200, token=token)
    expected = {
        **data,
        "companyId": created["companyId"],
        "currency": "DOP",
        "rnc": None,
        "status": "ACTIVE",
    }
    if profile != expected:
        raise SystemExit("GET /api/v1/companies/me returned an unexpected profile")
    print("Company profile smoke passed")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(f"Usage: python3 {sys.argv[0]} IDENTITY_BASE_URL COMPANY_BASE_URL")
    main(*sys.argv[1:])
