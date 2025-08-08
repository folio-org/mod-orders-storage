<#if mode.name() == "UPDATE">

insert into ${myuniversity}_mod_orders_storage.settings (id, jsonb)
  select cd.id,
         jsonb_build_object(
          'id', (cd.jsonb->>'id'),
          'key', (cd.jsonb->>'configName'),
          'value', (cd.jsonb->>'value'),
          'metadata', (cd.jsonb->'metadata')
        ) value
    from ${myuniversity}_mod_configuration.config_data cd
    where (cd.jsonb->>'module') = 'ORDERS'
      and not exists(
        select 1
          from ${myuniversity}_mod_orders_storage.settings s
          where (s.jsonb->>'key') = (cd.jsonb->>'configName')
      );

</#if>
