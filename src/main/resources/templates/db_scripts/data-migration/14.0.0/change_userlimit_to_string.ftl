-- Change eresource.userLimit from integer to string

<#if mode.name() == "UPDATE">
  UPDATE ${myuniversity}_${mymodule}.po_line
  SET jsonb = jsonb_set(jsonb, '{eresource, userLimit}', to_jsonb(jsonb->'eresource'->>'userLimit'))
  WHERE jsonb #> '{eresource, userLimit}' IS NOT NULL;
</#if>
