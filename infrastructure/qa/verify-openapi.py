"""Validate the public OpenAPI contracts served by a local or development Gateway."""
import argparse
import json
import subprocess
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import urlopen

from openapi_spec_validator import validate

MODULES = {
    "identity": ("/api/v1/auth/register", "post"),
    "company": ("/api/v1/companies", "post"),
    "catalog": ("/api/v1/clients", "get"),
    "inventory": ("/api/v1/inventory/stock", "get"),
    "sales": ("/api/v1/invoices", "post"),
    "expense": ("/api/v1/expenses", "post"),
    "finance": ("/api/v1/cash-sessions/active/summary", "get"),
}


def verify(origin, output, baseline):
    def read(path, media_type):
        with urlopen(origin + path, timeout=15) as response:
            assert response.headers.get_content_type() == media_type, path
            assert urlsplit(response.url).netloc == urlsplit(origin).netloc, path
            return response.read()

    def document(name, path):
        spec = json.loads(read(path, "application/json"))
        validate(spec)
        assert spec["servers"] == [{"url": "/"}], name
        assert spec["components"]["securitySchemes"]["bearerAuth"]["scheme"] == "bearer", name
        for route in spec.get("paths", {}):
            assert not route.startswith("/internal/"), route
        if output:
            current = output / f"{name}.json"
            current.write_text(json.dumps(spec, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
            if baseline:
                subprocess.run(["oasdiff", "breaking", str(baseline / current.name), str(current),
                                "--fail-on", "ERR"], check=True)
        return spec

    assert b"Swagger UI" in read("/swagger-ui.html", "text/html")
    config = json.loads(read("/v3/api-docs/swagger-config", "application/json"))
    assert {entry["url"] for entry in config["urls"]} == {
        f"/v3/api-docs/{name}" for name in MODULES
    } | {"/v3/api-docs"}
    gateway = document("gateway", "/v3/api-docs")
    assert "/actuator/health/readiness" in gateway["paths"]
    for name, (path, method) in MODULES.items():
        spec = document(name, f"/v3/api-docs/{name}")
        operation = spec["paths"][path][method]
        assert operation["tags"], name
        responses = operation["responses"]
        success = "201" if method == "post" else "200"
        assert success in responses, (name, list(responses))
        assert "X-Correlation-Id" in responses[success]["headers"], name
        if name == "identity":
            assert operation["security"] == []
        else:
            assert {"401", "403"} <= responses.keys(), name
        if name == "catalog":
            client = spec["paths"]["/api/v1/clients"]["post"]["requestBody"]
            assert client["content"]["application/json"]["schema"]["$ref"].endswith("/CreateClientRequest")
        if name == "inventory":
            parameters = {p["name"]: p for p in operation["parameters"]}
            assert {"search", "catalogItemIds", "warehouseId", "page", "size"} <= parameters.keys()
            assert parameters["catalogItemIds"]["schema"]["type"] == "array"
            assert parameters["X-Company-Id"]["required"]
        if name == "sales":
            schemas = spec["components"]["schemas"]

            def request_line(post):
                request = post["requestBody"]["content"]["application/json"]["schema"]
                body = schemas[request["$ref"].rsplit("/", 1)[-1]]
                item = body["properties"]["lines"]["items"]
                return schemas[item["$ref"].rsplit("/", 1)[-1]]

            assert "unitPrice" in request_line(operation)["required"]
            payment = spec["paths"]["/api/v1/invoices/{id}/payments"]["post"]
            assert {"amount", "method", "reference"} <= request_line(payment)["properties"].keys()
            assert {"400", "404", "409"} <= responses.keys()
            for status in ("400", "401", "403", "404", "409"):
                example = responses[status]["content"]["application/problem+json"]["example"]
                assert example["status"] == int(status) and example["errorCode"]
        if name == "expense":
            request = operation["requestBody"]["content"]["application/json"]["schema"]
            body = spec["components"]["schemas"][request["$ref"].rsplit("/", 1)[-1]]
            assert "warehouseId" in body["required"]
            cancel = spec["paths"]["/api/v1/expenses/{id}/cancel"]["post"]
            assert {"204", "400", "401", "403", "404", "409"} <= cancel["responses"].keys()
            schemas = spec["components"]["schemas"]
            assert "CANCELLED" in schemas["Detail"]["properties"]["status"]["enum"]
            assert "CANCELLED" not in schemas["Row"]["properties"]["status"]["enum"]
        print(f"OpenAPI valid: {name} ({len(spec['paths'])} paths)")
    print("Swagger UI and OpenAPI valid through Gateway")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("origin", help="Gateway origin for a local or development environment")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--baseline", type=Path, help="Compare published documents using oasdiff")
    args = parser.parse_args()
    if args.baseline and not args.output:
        parser.error("--baseline requires --output")
    if args.output:
        args.output.mkdir(parents=True, exist_ok=True)
    verify(args.origin.rstrip("/"), args.output, args.baseline)
