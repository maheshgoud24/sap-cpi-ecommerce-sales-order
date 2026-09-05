import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlParser
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    def originalOrder = message.getProperty("originalOrder")
    def salesOrderId = message.getProperty("salesOrderId")

    def orderXml = new XmlParser().parseText(originalOrder)

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.root {
        Insert_Statement1 {
            dbTableName(action: "INSERT") {
                table("SALES_ORDER_ITEM")

                orderXml.items.each { item ->
                    access {
                        SALES_ORDER_ID(salesOrderId)
                        ITEM_NO(item.itemNo.text())
                        MATERIAL_ID(item.materialId.text())
                        MATERIAL_DESCRIPTION("")
                        QUANTITY(item.quantity.text())
                        UOM("EA")
                        UNIT_PRICE(item.unitPrice.text())
                        NET_VALUE("")
                        PLANT("1000")
                    }
                }
            }
        }
    }

    message.setBody(writer.toString())

    return message
}