# Eventos de pagos V1

El [esquema JSON V1](src/main/resources/schemas/payment-event-v1.schema.json)
se distribuye con `common-events`.

Sales emite `ReceivablePaymentRegisteredV1` en `sales.receivable-payment.v1`.
El contrato prevé `PayablePaymentRegisteredV1` de Expense en `expense.payable-payment.v1`.
Cada evento representa una aplicación confirmada de un solo medio. Un pago mixto
comparte `paymentId` y emite un hecho por línea confirmada, con `sourceId` propio.
Finance usa `companyId` como clave Kafka, deduplica por `eventId` y protege el
efecto por `(companyId, origin, sourceId)`. Los reintentos conservan esos IDs.

```json
{
  "eventId": "00000000-0000-4000-8000-000000000001",
  "eventType": "ReceivablePaymentRegisteredV1",
  "version": 1,
  "occurredAt": "2026-11-26T14:00:00Z",
  "companyId": "00000000-0000-4000-8000-000000000002",
  "sourceId": "00000000-0000-4000-8000-000000000003",
  "traceId": "payment-request-1",
  "paymentId": "00000000-0000-4000-8000-000000000004",
  "shiftId": "00000000-0000-4000-8000-000000000005",
  "accountId": "00000000-0000-4000-8000-000000000006",
  "method": "CASH",
  "amount": "55.00",
  "currency": "DOP",
  "documentId": "00000000-0000-4000-8000-000000000009",
  "actorId": "00000000-0000-4000-8000-000000000010"
}
```

- `amount` es el importe aplicado neto, decimal positivo en la moneda ISO 4217 indicada por
  `currency`, representado como cadena JSON. En efectivo se descuenta el cambio.
- `occurredAt` es el instante UTC de confirmación del hecho. `shiftId` se omite
  para aplicaciones fuera de turno. `accountId` identifica la cuenta financiera.
- `documentId` y `actorId` identifican la factura y el operador autenticado
  del cobro. Los eventos V1 históricos pueden omitirlos.
- Cuando exista el flujo, `ReceivablePaymentReversedV1` y
  `PayablePaymentReversedV1` usan el topic y la clave del original. Conservan el
  `paymentId` del pago, añaden `originalEventId` y `originalSourceId`, y tienen
  `eventId` y `sourceId` propios. Su importe es la magnitud positiva revertida.
  Finance determina el sentido del movimiento a partir del tipo de evento.
- Ventas, facturas, compras, saldos iniciales e intentos pendientes quedan
  fuera de este contrato de confirmación.
- Los consumidores V1 toleran campos opcionales nuevos. Cambiar el significado
  de campos, añadir campos obligatorios o emitir tipos o medios desconocidos
  requiere coordinación con consumidores. Los cambios incompatibles requieren
  una versión mayor y su topic.

REST se usa para validaciones y lecturas inmediatas. Kafka transporta los
efectos financieros asíncronos mediante el outbox de S2-358 y el consumidor
idempotente de S2-359.
