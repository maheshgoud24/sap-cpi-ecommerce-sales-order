# Architecture — E-Commerce Sales Order Integration

## 1. Solution Overview

This integration implements an e-commerce Sales Order process using SAP Cloud Integration (CPI) and SAP HANA Cloud.

The solution receives an order as REST/JSON, validates the request, checks for duplicate orders, creates the Sales Order header, retrieves the generated Sales Order ID, creates multiple Sales Order items, and returns a business-friendly JSON response.

```text
E-Commerce / Postman
        |
        | HTTP POST / JSON
        v
+-----------------------------+
| SAP Cloud Integration (CPI)|
+--------------+--------------+
               |
               v
       JSON → XML Converter
               |
               v
         XML Validator
               |
               v
       Duplicate Order Check
               |
               v
            Router
          /         \
     EXISTS       NOT EXISTS
       |               |
       v               v
 Return Existing    Insert Header
    Order ID            |
                        v
                Get Sales Order ID
                        |
                        v
                Extract Order ID
                        |
                        v
                Restore Original
                     Order
                        |
                        v
                Build Item Payload
                        |
                        v
                 JDBC Batch Mode
                        |
                        v
                 SAP HANA Cloud
                        |
                        v
                 JSON Response
2. Source System
E-Commerce Application

The source system represents an external e-commerce application.

During development and testing, Postman is used to simulate this application.

The source sends:

Order ID
Customer ID
Order date
Sales organization
Distribution channel
Order items
Material ID
Quantity
Unit price

Example:

{
  "orderId": "WEB10001",
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
3. HTTP Sender

The CPI integration uses an HTTP Sender Adapter to expose the integration endpoint.

HTTP POST
    ↓
SAP CPI

The sender receives the JSON request synchronously.

The response from CPI is returned to the calling system.

4. JSON to XML Conversion

The incoming JSON payload is converted to XML using the JSON to XML Converter.

This allows the message to be processed using XML-based CPI components and XSD validation.

Example:

JSON
 ↓
JSON to XML Converter
 ↓
XML

The converter also handles the order's repeated items elements.

5. XML Validation

An XSD is used to validate the incoming order structure.

Validation includes:

Required fields
Non-empty values
At least one order item
Quantity greater than zero
Correct data structure

Invalid requests are rejected before the target database is called.

This protects the target system from invalid business requests.

6. Exchange Properties

Important values are stored as CPI Exchange Properties.

Examples:

orderId
customerId
orderDate
salesOrg
distributionChannel
originalOrder
salesOrderId
Why use Exchange Properties?

The message body changes several times during the integration.

For example:

Original Order XML
        ↓
HANA SELECT response
        ↓
Sales Order ID XML
        ↓
Original Order XML restored
        ↓
JDBC Item INSERT XML

The original order and important business values therefore need to be preserved independently from the message body.

7. Duplicate Order Check

Before creating a Sales Order, CPI checks whether the external order already exists.

The source orderId is stored in:

EXTERNAL_ORDER_ID

The database query checks:

SELECT SALES_ORDER_ID, EXTERNAL_ORDER_ID
FROM SALES_ORDER
WHERE EXTERNAL_ORDER_ID = '${property.orderId}'

This implements idempotency.

8. Router

A Router determines whether the order already exists.

                 Router
                /      \
               /        \
          EXISTS       NOT EXISTS
             |             |
             v             v
       Return Existing   Create New

The EXISTS condition checks whether the HANA SELECT returned a row.

Example condition:

count(/ROOT/select_response/row) > 0

The default route handles the NOT EXISTS scenario.

9. EXISTS Branch

If the order already exists:

Duplicate Found
      ↓
Extract existing SALES_ORDER_ID
      ↓
Return JSON response

Example:

{
  "status": "SUCCESS",
  "salesOrderId": "20",
  "message": "Order already exists"
}

No new Sales Order header or items are created.

10. NOT EXISTS Branch

For a new order, CPI first creates the Sales Order header.

The header contains:

EXTERNAL_ORDER_ID
CUSTOMER_ID
ORDER_DATE
SALES_ORG
DISTRIBUTION_CHANNEL
STATUS

Example status:

CREATED

After the INSERT, CPI performs a SELECT to retrieve the generated internal Sales Order ID.

11. Sales Order ID Retrieval

The generated Sales Order ID is retrieved from HANA.

Example JDBC response:

<ROOT>
  <select_response>
    <row>
      <SALES_ORDER_ID>20</SALES_ORDER_ID>
    </row>
  </select_response>
</ROOT>

A Groovy script extracts the value:

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlParser

def Message processData(Message message) {

    def body = message.getBody(String)

    def xml = new XmlParser().parseText(body)

    def salesOrderId = xml.select_response.row.SALES_ORDER_ID.text()

    message.setProperty("salesOrderId", salesOrderId)

    return message
}

The value is stored as:

salesOrderId
12. Original Order Restoration

The original order XML is stored in the exchange property:

originalOrder

After retrieving the generated Sales Order ID, the original order is restored.

This is important because the JDBC SELECT response has replaced the message body.

The flow therefore becomes:

Original Order
     ↓
Store as Property
     ↓
HANA SELECT
     ↓
Sales Order ID
     ↓
Restore Original Order
13. Multiple Item Processing

The original order can contain multiple items.

The Groovy script loops through all items:

orderXml.items.each { item ->
    ...
}

For every item, the same generated Sales Order ID is used.

Example:

Sales Order ID = 19

Item 20 → MAT101
Item 30 → MAT102

This ensures that all items belong to the correct Sales Order header.

14. JDBC Item Payload

Groovy generates the JDBC XML structure dynamically.

Each item is represented by an <access> element.

Example:

<access>
    <SALES_ORDER_ID>19</SALES_ORDER_ID>
    <ITEM_NO>20</ITEM_NO>
    <MATERIAL_ID>MAT101</MATERIAL_ID>
    <QUANTITY>2</QUANTITY>
    <UOM>EA</UOM>
    <UNIT_PRICE>1500</UNIT_PRICE>
    <PLANT>1000</PLANT>
</access>

Multiple <access> elements are generated for multiple items.

15. JDBC Receiver

The JDBC Receiver Adapter connects CPI to SAP HANA Cloud.

The integration uses the configured HANA Cloud JDBC Data Source.

The JDBC adapter is used for:

Duplicate checking
Sales Order header INSERT
Sales Order ID SELECT
Sales Order item INSERT
16. JDBC Batch Mode

Batch Mode is enabled for the Sales Order item insertion.

This allows multiple item records to be inserted from the same JDBC payload.

Example:

Item 1
   +
Item 2
   +
Item 3
   ↓
JDBC Batch
   ↓
HANA

For two items, the JDBC response confirmed:

<insert_count>2</insert_count>

This proves that both item records were processed.

17. Error Handling

An Exception Subprocess is used to handle errors.

The integration returns a structured error response rather than exposing technical database details to the source system.

Example:

{
  "status": "ERROR",
  "message": "Invalid order request"
}

The HTTP response code for validation failures is:

400 Bad Request
18. Final Success Response

After the header and all items are successfully created, CPI returns a business-friendly JSON response.

Example:

{
  "status": "SUCCESS",
  "salesOrderId": "20",
  "message": "Order created successfully"
}

The internal JDBC XML response is not exposed to the e-commerce application.

19. End-to-End Processing

The complete processing sequence is:

1. Receive JSON
       ↓
2. Convert JSON → XML
       ↓
3. Validate XML
       ↓
4. Store required properties
       ↓
5. Check duplicate order
       ↓
6. Router
       ↓
7. If EXISTS → Return existing ID
       ↓
8. If NOT EXISTS → Insert Sales Order header
       ↓
9. Retrieve generated Sales Order ID
       ↓
10. Store Sales Order ID
       ↓
11. Restore original order
       ↓
12. Generate item INSERT payload
       ↓
13. JDBC Batch INSERT
       ↓
14. Return success JSON
20. Integration Patterns Used

This project demonstrates several common enterprise integration patterns.

Request-Reply

Used when CPI needs an immediate response from HANA.

CPI → HANA
CPI ← HANA
Content-Based Routing

The Router determines processing based on whether the order exists.

Idempotent Receiver

The external order ID prevents duplicate processing.

Message Transformation

JSON is converted to XML and later transformed into JDBC XML.

Exception Handling

The Exception Subprocess handles invalid requests and runtime errors.

Batch Processing

Multiple order items are processed using JDBC Batch Mode.

21. Target System

For this training implementation, SAP HANA Cloud is used as the persistence layer.

SAP CPI
   |
   | JDBC
   v
SAP HANA Cloud

The target contains:

SALES_ORDER
SALES_ORDER_ITEM

The Sales Order header and item relationship is maintained using SALES_ORDER_ID.

22. Future S/4HANA Cloud Architecture

The current HANA target is used for training.

In a production implementation, the target can be changed to SAP S/4HANA Cloud.

Future architecture:

E-Commerce
     |
     | REST / JSON
     v
SAP CPI
     |
     | OData
     v
SAP S/4HANA Cloud
     |
     v
Sales Order

The CPI design principles remain the same:

Validation
Routing
Idempotency
Error handling
Transformation
Multi-item processing
Response handling

Only the target communication layer would change from JDBC/HANA to the appropriate S/4HANA API.

23. Security

Sensitive credentials must never be stored in GitHub.

The following should not be committed:

Passwords
API credentials
Client secrets
Access tokens
Private keys
Database credentials

CPI credentials and connection information should be maintained using appropriate SAP Integration Suite security mechanisms.

24. Conclusion

This integration demonstrates a practical SAP CPI implementation for an e-commerce Sales Order scenario.

The solution successfully handles:

REST/JSON inbound communication
XML transformation
XSD validation
Business validation
Duplicate detection
Idempotency
Sales Order header creation
Generated Sales Order ID retrieval
Multiple item processing
JDBC Batch Mode
SAP HANA Cloud integration
Exception handling
Structured JSON responses

The project provides a foundation that can later be extended from SAP HANA Cloud to SAP S/4HANA Cloud using standard APIs.


### Commit it

Use this commit message:

```text
Add CPI integration architecture documentation
