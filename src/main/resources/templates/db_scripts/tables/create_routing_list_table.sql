CREATE TABLE routing_list
(
    id uuid NOT NULL PRIMARY KEY,
    name text NOT NULL UNIQUE,
    notes text,
    "userIds" uuid[] NOT NULL,
    "poLineId" uuid NOT NULL,
    FOREIGN KEY ("poLineId") REFERENCES po_line (id) MATCH SIMPLE ON UPDATE CASCADE ON DELETE CASCADE
);
