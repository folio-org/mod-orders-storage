package org.folio.rest.impl;

import javax.annotation.Generated;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
        "id",
        "alert",
        "po_line_id"
})

public class Alert {

    /**
     * UUID
     *
     */
    @JsonProperty("id")
    @Pattern(regexp = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    private String id;
    @JsonProperty("alert")
    private String alert;
    /**
     * UUID
     * (Required)
     *
     */
    @JsonProperty("po_line_id")
    @Pattern(regexp = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
    @NotNull
    private String poLineId;

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

    public Alert withId(String id) {
        this.id = id;
        return this;
    }

    /**
     *
     * @return
     *     The alert
     */
    @JsonProperty("alert")
    public String getAlert() {
        return alert;
    }

    /**
     *
     * @param alert
     *     The alert
     */
    @JsonProperty("alert")
    public void setAlert(String alert) {
        this.alert = alert;
    }

    public Alert withAlert(String alert) {
        this.alert = alert;
        return this;
    }

    /**
     * UUID
     * (Required)
     *
     * @return
     *     The poLineId
     */
    @JsonProperty("po_line_id")
    public String getPoLineId() {
        return poLineId;
    }

    /**
     * UUID
     * (Required)
     *
     * @param poLineId
     *     The po_line_id
     */
    @JsonProperty("po_line_id")
    public void setPoLineId(String poLineId) {
        this.poLineId = poLineId;
    }

    public Alert withPoLineId(String poLineId) {
        this.poLineId = poLineId;
        return this;
    }
}
