
package org.folio.rest.impl;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "purchase_orders",
    "total_records",
    "first",
    "last"
})
public class PurchaseOrderCollection {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("purchase_orders")
    @Valid
    @NotNull
    private List<PurchaseOrder> purchaseOrders = new ArrayList<PurchaseOrder>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("total_records")
    @NotNull
    private Integer totalRecords;
    @JsonProperty("first")
    private Integer first;
    @JsonProperty("last")
    private Integer last;

    /**
     * 
     * (Required)
     * 
     * @return
     *     The purchaseOrders
     */
    @JsonProperty("purchase_orders")
    public List<PurchaseOrder> getPurchaseOrders() {
        return purchaseOrders;
    }

    /**
     * 
     * (Required)
     * 
     * @param purchaseOrders
     *     The purchase_orders
     */
    @JsonProperty("purchase_orders")
    public void setPurchaseOrders(List<PurchaseOrder> purchaseOrders) {
        this.purchaseOrders = purchaseOrders;
    }

    public PurchaseOrderCollection withPurchaseOrders(List<PurchaseOrder> purchaseOrders) {
        this.purchaseOrders = purchaseOrders;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The totalRecords
     */
    @JsonProperty("total_records")
    public Integer getTotalRecords() {
        return totalRecords;
    }

    /**
     * 
     * (Required)
     * 
     * @param totalRecords
     *     The total_records
     */
    @JsonProperty("total_records")
    public void setTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
    }

    public PurchaseOrderCollection withTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
        return this;
    }

    /**
     * 
     * @return
     *     The first
     */
    @JsonProperty("first")
    public Integer getFirst() {
        return first;
    }

    /**
     * 
     * @param first
     *     The first
     */
    @JsonProperty("first")
    public void setFirst(Integer first) {
        this.first = first;
    }

    public PurchaseOrderCollection withFirst(Integer first) {
        this.first = first;
        return this;
    }

    /**
     * 
     * @return
     *     The last
     */
    @JsonProperty("last")
    public Integer getLast() {
        return last;
    }

    /**
     * 
     * @param last
     *     The last
     */
    @JsonProperty("last")
    public void setLast(Integer last) {
        this.last = last;
    }

    public PurchaseOrderCollection withLast(Integer last) {
        this.last = last;
        return this;
    }

}
