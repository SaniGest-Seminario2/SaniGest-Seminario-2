#!/usr/bin/env python3
"""Compare confirmed financial sources and Finance projections for one company."""
import argparse
from datetime import datetime, timedelta, timezone
import os
import subprocess
import sys
from uuid import UUID

SALES = """
    SELECT line.id AS source_id, payment.registered_at AS registered_at
    FROM sales.invoice_payments payment
    JOIN sales.invoice_payment_lines line ON line.payment_id = payment.id
    WHERE payment.company_id = :'company'::uuid AND payment.registered_at IS NOT NULL
"""
EXPENSE = """
    SELECT id AS source_id,
           CASE jsonb_typeof(response_body->'registeredAt')
               WHEN 'number' THEN to_timestamp((response_body->>'registeredAt')::double precision)
               WHEN 'string' THEN (response_body->>'registeredAt')::timestamptz
           END AS registered_at
    FROM expense.payable_payments
    WHERE company_id = :'company'::uuid AND response_body IS NOT NULL
"""


def query(service, sql, company, cutoff):
    # libpq service definitions supply separate credentials for each service database.
    result = subprocess.run(
        ["psql", "-XqAt", "-v", "ON_ERROR_STOP=1",
         "-v", f"company={company}", "-v", f"cutoff={cutoff.isoformat()}"],
        env={**os.environ, "PGSERVICE": service},
        input=f"BEGIN READ ONLY;\n{sql};\nCOMMIT;\n",
        capture_output=True, text=True, check=True, timeout=60,
    )
    return [line.split("|") for line in result.stdout.splitlines()]


def read_sources(schema, origin, payments_sql, company, cutoff):
    sql = f"""
        SELECT source_id,
               COALESCE(registered_at < :'cutoff'::timestamptz, true)
        FROM (
            {payments_sql}
            UNION ALL
            SELECT source_id, created_at
            FROM {schema}.payment_outbox
            WHERE company_id = :'company'::uuid AND original_event_id IS NOT NULL
        ) sources
    """
    return {
        (company, origin, source_id): mature == "t"
        for source_id, mature in query(f"sanigest_{schema}", sql, company, cutoff)
    }


def reconcile(company, cutoff):
    # Read Finance first so sources committed before projection are visible to the later reads.
    movements = {
        (company, origin, source_id)
        for origin, source_id in query(
            "sanigest_finance",
            """
            SELECT origin, source_id FROM finance.cash_movements
            WHERE company_id = :'company'::uuid AND origin IN ('COBRO', 'PAGO')
            """,
            company, cutoff,
        )
    }
    sources = read_sources("sales", "COBRO", SALES, company, cutoff)
    sources.update(read_sources("expense", "PAGO", EXPENSE, company, cutoff))
    missing = {key for key, mature in sources.items() if mature} - movements
    orphaned = movements - sources.keys()
    for label, keys in (("MISSING_MOVEMENT", missing), ("ORPHAN_MOVEMENT", orphaned)):
        for key in sorted(keys):
            print(label, *key)
    return int(bool(missing or orphaned))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--company", type=UUID, required=True)
    parser.add_argument("--grace-minutes", type=int, default=15)
    args = parser.parse_args()
    if args.grace_minutes < 0:
        parser.error("--grace-minutes must be nonnegative")
    cutoff = datetime.now(timezone.utc) - timedelta(minutes=args.grace_minutes)
    return reconcile(str(args.company), cutoff)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.CalledProcessError as error:
        print(error.stderr.strip(), file=sys.stderr)
        sys.exit(2)
    except (OSError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        sys.exit(2)
