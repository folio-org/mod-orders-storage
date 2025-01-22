CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.wrapper_pieces_view AS
select t.id,
			 jsonb_build_object(
         'vendorId', (e.jsonb->>'vendor'),
       	 'piece', t.jsonb,
       	 'poLine', c.jsonb,
       	 'title', d.jsonb,
       	 'purchaseOrder', e.jsonb
       ) jsonb,
			 t.creation_date,
			 t.created_by,
			 t.polineid,
			 t.titleid
  from ${myuniversity}_${mymodule}.pieces t
    join ${myuniversity}_${mymodule}.po_line c on c.id = t.polineid
    join ${myuniversity}_${mymodule}.titles d on d.id = t.titleId
    join ${myuniversity}_${mymodule}.purchase_order e on e.id = c.purchaseorderid
;
