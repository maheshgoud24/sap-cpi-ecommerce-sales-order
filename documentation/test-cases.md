# Test Cases — E-Commerce Sales Order Integration

## Overview

The integration flow was tested using Postman as the e-commerce source and SAP HANA Cloud SQL Console as the target verification system.

The following scenarios were tested.

---

## Test Case 01 — Valid Single-Item Order

### Input

A valid order containing one item is sent from Postman.

```json
{
  "orderId": "WEBTEST001",
  "customerId": "CUST1001",
  "orderDate": "2026-09-07",
  "salesOrg": "1000",
  "distributionChannel": "10",
  "items": [
    {
      "itemNo": 10,
      "materialId": "MAT101",
      "quantity": 2,
      "unitPrice": 1500
    }
  ]
}
Expected Result
Request is accepted.
Sales Order header is created.
Sales Order item is created.
Generated Sales Order ID is returned.
Result

PASS ✅

Test Case 02 — Valid Multiple-Item Order
Input

An order containing multiple items is sent.

{
  "orderId": "WEBMULTI001",
  "customerId": "CUST1001",
  "orderDate": "2026-09-07",
  "salesOrg": "1000",
  "distributionChannel": "10",
  "items": [
    {
      "itemNo": 20,
      "materialId": "MAT101",
      "quantity": 2,
      "unitPrice": 1500
    },
    {
      "itemNo": 30,
      "materialId": "MAT102",
      "quantity": 5,
      "unitPrice": 2500
    }
  ]
}
Expected Result

One Sales Order header should be created with two item records.

Sales Order
    |
    +-- Item 20 → MAT101
    |
    +-- Item 30 → MAT102
Result

PASS ✅

The JDBC batch response confirmed:

<insert_count>2</insert_count>
Test Case 03 — Missing Customer ID
Input

The customerId field is missing or blank.

Expected Result

The request must fail validation.

HTTP 400 Bad Request

The target HANA database must not be called.

Result

PASS ✅

Test Case 04 — Quantity = 0
Input
{
  "itemNo": 10,
  "materialId": "MAT101",
  "quantity": 0,
  "unitPrice": 1500
}
Expected Result

The request must be rejected because quantity must be greater than zero.

HTTP 400 Bad Request
Result

PASS ✅

Test Case 05 — Negative Quantity
Input
{
  "itemNo": 10,
  "materialId": "MAT101",
  "quantity": -2,
  "unitPrice": 1500
}
Expected Result

The request must be rejected during validation.

Result

PASS ✅

Test Case 06 — Empty Items Array
Input
{
  "orderId": "WEBEMPTY001",
  "customerId": "CUST1001",
  "orderDate": "2026-09-07",
  "salesOrg": "1000",
  "distributionChannel": "10",
  "items": []
}
Expected Result

The request must be rejected because at least one item is required.

Result

PASS ✅

Test Case 07 — Duplicate Order / Idempotency
Scenario

The same e-commerce order is submitted more than once using the same orderId.

Example:

orderId = WEBMULTI001
Expected Result

CPI checks EXTERNAL_ORDER_ID in HANA before creating a new order.

If the order already exists:

A new Sales Order header must not be created.
The existing Sales Order ID must be returned.
Example Response
{
  "status": "SUCCESS",
  "salesOrderId": "20",
  "message": "Order already exists"
}
Result

PASS ✅

Test Case 08 — Sales Order ID Retrieval
Scenario

After creating the Sales Order header, CPI executes a SELECT statement to retrieve the generated internal Sales Order ID.

Expected Result

The generated ID is extracted from the JDBC XML response.

Example:

<ROOT>
  <select_response>
    <row>
      <SALES_ORDER_ID>20</SALES_ORDER_ID>
    </row>
  </select_response>
</ROOT>

The value is stored in the CPI exchange property:

salesOrderId
Result

PASS ✅

Test Case 09 — Item Creation Against Generated Sales Order
Scenario

The generated Sales Order ID is used while creating the Sales Order items.

Expected Result

All items must contain the same generated Sales Order ID.

Example:

SALES_ORDER_ID = 19

ITEM 20 → MAT101
ITEM 30 → MAT102
Result

PASS ✅

Test Summary
Test Case	Scenario	Result
TC01	Valid single-item order	PASS ✅
TC02	Valid multiple-item order	PASS ✅
TC03	Missing customer ID	PASS ✅
TC04	Quantity = 0	PASS ✅
TC05	Negative quantity	PASS ✅
TC06	Empty items array	PASS ✅
TC07	Duplicate order / Idempotency	PASS ✅
TC08	Sales Order ID retrieval	PASS ✅
TC09	Item creation	PASS ✅
End-to-End Validation

The complete integration was validated across:

Postman
   ↓
SAP Cloud Integration
   ↓
Validation
   ↓
Duplicate Check
   ↓
Sales Order Header
   ↓
Sales Order ID Retrieval
   ↓
Multiple Item Processing
   ↓
SAP HANA Cloud
   ↓
JSON Response

Overall Integration Status: PASS ✅


### Commit it

Use this commit message:

```text
Add integration test cases
