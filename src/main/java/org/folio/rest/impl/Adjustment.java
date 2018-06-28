
package org.folio.rest.jaxrs.model;

import javax.annotation.Generated;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
        "id",
        "shipment",
    "insurance",
    "discount",
    "overhead",
    "tax",
    "use_pro_rate",
        "invoice_id"
})
public class Adjustment {
    /**
     * UUID
     *
     */
    @JsonProperty("id")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String id;
    @JsonProperty("shipment")
    private Double shipment;
    @JsonProperty("insurance")
    private Double insurance;
    @JsonProperty("discount")
    private Double discount;
    @JsonProperty("overhead")
    private Double overhead;
    @JsonProperty("tax")
    private Double tax;
    @JsonProperty("use_pro_rate")
    private Boolean useProRate;
    @JsonProperty("invoice_id")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String invoiceId;

    /**
     * UUID
     *
     * @return
     *     The id
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * UUID
     *
     * @param id
     *     The id
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Adjustment withId(String id) {
        this.id = id;
        return this;
    }

    /**
     * 
     * @return
     *     The shipment
     */
    @JsonProperty("shipment")
    public Double getShipment() {
        return shipment;
    }

    /**
     * 
     * @param shipment
     *     The shipment
     */
    @JsonProperty("shipment")
    public void setShipment(Double shipment) {
        this.shipment = shipment;
    }

    public Adjustment withShipment(Double shipment) {
        this.shipment = shipment;
        return this;
    }

    /**
     * 
     * @return
     *     The insurance
     */
    @JsonProperty("insurance")
    public Double getInsurance() {
        return insurance;
    }

    /**
     * 
     * @param insurance
     *     The insurance
     */
    @JsonProperty("insurance")
    public void setInsurance(Double insurance) {
        this.insurance = insurance;
    }

    public Adjustment withInsurance(Double insurance) {
        this.insurance = insurance;
        return this;
    }

    /**
     * 
     * @return
     *     The discount
     */
    @JsonProperty("discount")
    public Double getDiscount() {
        return discount;
    }

    /**
     * 
     * @param discount
     *     The discount
     */
    @JsonProperty("discount")
    public void setDiscount(Double discount) {
        this.discount = discount;
    }

    public Adjustment withDiscount(Double discount) {
        this.discount = discount;
        return this;
    }

    /**
     * 
     * @return
     *     The overhead
     */
    @JsonProperty("overhead")
    public Double getOverhead() {
        return overhead;
    }

    /**
     * 
     * @param overhead
     *     The overhead
     */
    @JsonProperty("overhead")
    public void setOverhead(Double overhead) {
        this.overhead = overhead;
    }

    public Adjustment withOverhead(Double overhead) {
        this.overhead = overhead;
        return this;
    }

    /**
     * 
     * @return
     *     The tax
     */
    @JsonProperty("tax")
    public Double getTax() {
        return tax;
    }

    /**
     * 
     * @param tax
     *     The tax
     */
    @JsonProperty("tax")
    public void setTax(Double tax1) {
        this.tax = tax;
    }

    public Adjustment withTax1(Double tax1) {
        this.tax = tax;
        return this;
    }

    /**
     * 
     * @return
     *     The useProRate
     */
    @JsonProperty("use_pro_rate")
    public Boolean getUseProRate() {
        return useProRate;
    }

    /**
     * 
     * @param useProRate
     *     The use_pro_rate
     */
    @JsonProperty("use_pro_rate")
    public void setUseProRate(Boolean useProRate) {
        this.useProRate = useProRate;
    }

    public Adjustment withUseProRate(Boolean useProRate) {
        this.useProRate = useProRate;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The invoiceId
     */
    @JsonProperty("invoice_id")
    public String getInvoiceId() {
        return invoiceId;
    }

    /**
     * UUID
     *
     * @param invoiceId
     *     The invoice_id
     */
    @JsonProperty("invoice_id")
    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Adjustment withInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
        return this;
    }

}
