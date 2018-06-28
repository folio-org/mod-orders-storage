
package org.folio.rest.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
        "id",
        "po_number",
        "created",
        "comments",
        "total_items",
        "total_estimated_price",
        "adjustments",
        "assigned_to",
        "created_by",
        "transmission_date",
        "transmission_method",
        "po_workflow_status_id",
        "po_receipt_status",
        "po_payment_status"
})
public class PurchaseOrder {

    /**
     * UUID
     *
     */
    @JsonProperty("id")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String id;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("po_number")
    @NotNull
    private String poNumber;
    @JsonProperty("created")
    private Date created;
    @JsonProperty("comments")
    private String comments;
    @JsonProperty("total_items")
    private Integer totalItems;
    @JsonProperty("total_estimated_price")
    private Double totalEstimatedPrice;
    @JsonProperty("adjustments")
    private String adjustments;
    /**
     * UUID
     *
     */
    @JsonProperty("assigned_to")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String assignedTo;
    /**
     * UUID
     *
     */
    @JsonProperty("created_by")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String createdBy;
    @JsonProperty("transmission_date")
    private Date transmissionDate;
    @JsonProperty("transmission_method")
    private String transmissionMethod;
    /**
     * UUID
     *
     */
    @JsonProperty("po_workflow_status_id")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String poWorkflowStatusId;
    /**
     * UUID
     *
     */
    @JsonProperty("po_receipt_status")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String poReceiptStatus;
    /**
     * UUID
     *
     */
    @JsonProperty("po_payment_status")
    @Pattern(regexp = "^[a-f0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String poPaymentStatus;


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

    public PurchaseOrder withId(String id) {
        this.id = id;
        return this;
    }

    /**
     *
     * (Required)
     *
     * @return
     *     The poNumber
     */
    @JsonProperty("po_number")
    public String getPoNumber() {
        return poNumber;
    }

    /**
     *
     * (Required)
     *
     * @param poNumber
     *     The po_number
     */
    @JsonProperty("po_number")
    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public PurchaseOrder withPoNumber(String poNumber) {
        this.poNumber = poNumber;
        return this;
    }

    /**
     *
     * @return
     *     The comments
     */
    @JsonProperty("comments")
    public String getComments() {
        return comments;
    }

    /**
     *
     * @param comments
     *     The comments
     */
    @JsonProperty("comments")
    public void setComments(String comments) {
        this.comments = comments;
    }

    public PurchaseOrder withComments(String comments) {
        this.comments = comments;
        return this;
    }

    /**
     *
     * @return
     *     The totalItems
     */
    @JsonProperty("total_items")
    public Integer getTotalItems() {
        return totalItems;
    }

    /**
     *
     * @param totalItems
     *     The totalItems
     */
    @JsonProperty("total_items")
    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public PurchaseOrder withTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
        return this;
    }

    /**
     *
     * @return
     *     The totalEstimatedPrice
     */
    @JsonProperty("total_estimated_price")
    public Double getTotalEstimatedPrice() {
        return totalEstimatedPrice;
    }

    /**
     *
     * @param totalEstimatedPrice
     *     The totalEstimated
     */
    @JsonProperty("total_estimated_price")
    public void setTotalEstimatedPrice(Double totalEstimatedPrice) {
        this.totalEstimatedPrice = totalEstimatedPrice;
    }

    public PurchaseOrder withTotalEstimatedPrice(Double totalEstimatedPrice) {
        this.totalEstimatedPrice = totalEstimatedPrice;
        return this;
    }

    /**
     *
     * @return
     *     The adjustments
     */
    @JsonProperty("adjustments")
    public String getAdjustments() {
        return adjustments;
    }

    /**
     *
     * @param adjustments
     *     The adjustments
     */
    @JsonProperty("adjustments")
    public void setAdjustments(String adjustments) {
        this.adjustments = adjustments;
    }

    public PurchaseOrder withAdjustments(String adjustments) {
        this.adjustments = adjustments;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The assignedTo
     */
    @JsonProperty("assigned_to")
    public String getAssignedTo() {
        return assignedTo;
    }

    /**
     * UUID
     *
     * @param assignedTo
     *     The assigned_to
     */
    @JsonProperty("assigned_to")
    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public PurchaseOrder withAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The createdBy
     */
    @JsonProperty("created_by")
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * UUID
     *
     * @param createdBy
     *     The created_by
     */
    @JsonProperty("created_by")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public PurchaseOrder withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /**
     *
     * @return
     *     The transmissionDate
     */
    @JsonProperty("transmission_date")
    public Date getTransmissionDate() {
        return transmissionDate;
    }

    /**
     *
     * @param transmissionDate
     *     The transmission
     */
    @JsonProperty("transmissionDate")
    public void setTransmissionDate(Date transmissionDate) {
        this.transmissionDate = transmissionDate;
    }

    public PurchaseOrder withTransmissionDate(Date transmissionDate) {
        this.transmissionDate = transmissionDate;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The transmissionMethod
     */
    @JsonProperty("transmission_method")
    public String getTransmissionMethod() {
        return transmissionMethod;
    }

    /**
     * UUID
     *
     * @param transmissionMethod
     *     The transmissionMethod
     */
    @JsonProperty("transmission_method")
    public void setTransmissionMethod(String transmissionMethod) {
        this.transmissionMethod = transmissionMethod;
    }

    public PurchaseOrder withTransmissionMethod(String transmissionMethod) {
        this.transmissionMethod = transmissionMethod;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The poWorkflowStatusId
     */
    @JsonProperty("po_workflow_status_id")
    public String getPoWorkflowStatusId() {
        return poWorkflowStatusId;
    }

    /**
     * UUID
     *
     * @param poWorkflowStatusId
     *     The poWorkflowStatusId
     */
    @JsonProperty("po_workflow_status_id")
    public void setPoWorkflowStatusId(String poWorkflowStatusId) {
        this.poWorkflowStatusId = poWorkflowStatusId;
    }

    public PurchaseOrder withPoWorkflowStatusId(String poWorkflowStatusId) {
        this.poWorkflowStatusId = poWorkflowStatusId;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The poReceiptStatus
     */
    @JsonProperty("po_receipt_status")
    public String getPoReceiptStatus() {
        return poReceiptStatus;
    }

    /**
     * UUID
     *
     * @param poReceiptStatus
     *     The poReceiptStatus
     */
    @JsonProperty("po_receipt_status")
    public void setPoReceiptStatus(String poReceiptStatus) {
        this.poReceiptStatus = poReceiptStatus;
    }

    public PurchaseOrder withPoReceiptStatus(String poReceiptStatus) {
        this.poReceiptStatus = poReceiptStatus;
        return this;
    }

    /**
     * UUID
     *
     * @return
     *     The poPaymentStatus
     */
    @JsonProperty("po_payment_status")
    public String getPoPaymentStatus() {
        return poPaymentStatus;
    }

    /**
     * UUID
     *
     * @param poPaymentStatus
     *     The poPaymentStatus
     */
    @JsonProperty("po_payment_status")
    public void setPoPaymentStatus(String poPaymentStatus) {
        this.poPaymentStatus = poPaymentStatus;
    }

    public PurchaseOrder withPoPaymentStatus(String poPaymentStatus) {
        this.poPaymentStatus = poPaymentStatus;
        return this;
    }


    /**
     *
     * @return
     *     The changelog
     */
    /**@JsonProperty("changelog")
    public List<Changelog> getChangelog() {
        return changelog;
    }*/

    /**
     *
     * @param changelog
     *     The changelog
     */
    /**@JsonProperty("changelog")
    public void setChangelog(List<Changelog> changelog) {
        this.changelog = changelog;
    }

    public PurchaseOrder withChangelog(List<Changelog> changelog) {
        this.changelog = changelog;
        return this;
    }*/

}
