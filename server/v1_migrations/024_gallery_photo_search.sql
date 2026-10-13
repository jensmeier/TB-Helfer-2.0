-- Read-only decoding for searchable legacy photo metadata. No file data rewrite.
CREATE FUNCTION tb.gallery_photo_metadata(filename text, structured jsonb, overrides jsonb)
RETURNS jsonb LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    raw text; data bytea; base jsonb := '{}'::jsonb;
    pos integer := 33; size integer; field text; value text;
    bits bigint; exponent integer; fraction bigint; km double precision;
BEGIN
    BEGIN
        IF left(filename,7)='BTBP1__' THEN
            raw := split_part(substr(filename, 8), '__', 1);
            data := decode(translate(raw, '-_', '+/') || repeat('=', (4-length(raw)%4)%4), 'base64');
            IF length(data) >= 33 THEN
                FOREACH field IN ARRAY ARRAY['firma','kurztext','objektKennung','objektBereich'] LOOP
                    size := get_byte(data, pos); pos := pos + 1;
                    IF pos + size > length(data) THEN RAISE EXCEPTION 'short metadata'; END IF;
                    value := convert_from(substring(data FROM pos+1 FOR size), 'UTF8');
                    pos := pos + size;
                    IF value <> '' THEN base := base || jsonb_build_object(field,value); END IF;
                END LOOP;
                base := base || jsonb_build_object(
                    'typ',(ARRAY['ABNAHMEN','EINWEISUNG_UNTERWEISUNG','BESONDERHEITEN','MAENGEL_SCHAEDEN','FOTODOKUMENTATION'])[get_byte(data,31)+1],
                    'indikator',(ARRAY['GRUEN','GELB','ROT'])[get_byte(data,32)+1]);
                bits := get_byte(data,20)::bigint*16777216 + get_byte(data,21)::bigint*65536
                    + get_byte(data,22)::bigint*256 + get_byte(data,23);
                exponent := ((bits >> 23) & 255)::integer; fraction := bits & 8388607;
                IF exponent <> 255 THEN
                    km := CASE WHEN exponent=0 THEN fraction*power(2::double precision,-149)
                        ELSE (1+fraction/8388608::double precision)*power(2::double precision,exponent-127) END;
                    IF (bits & 2147483648) <> 0 THEN km := -km; END IF;
                    base := base || jsonb_build_object('km',km);
                END IF;
            END IF;
        ELSIF left(filename,10)='BTBPHOTO__' THEN
            raw := split_part(substr(filename, 11), '__', 1);
            base := convert_from(decode(translate(raw,'-_','+/') || repeat('=',(4-length(raw)%4)%4),'base64'),'UTF8')::jsonb;
            IF jsonb_typeof(base) <> 'object' THEN base := '{}'::jsonb; END IF;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        base := '{}'::jsonb;
    END;
    IF jsonb_typeof(structured)='object' THEN base := base || jsonb_strip_nulls(structured); END IF;
    IF jsonb_typeof(overrides)='object' THEN base := base || overrides; END IF;
    RETURN jsonb_strip_nulls(base);
END;
$$;

CREATE FUNCTION tb.gallery_photo_matches(meta jsonb, extra text, terms text[], km_min bigint, km_max bigint)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    haystack text; term text; meter numeric;
BEGIN
    SELECT lower(coalesce(extra,'') || ' ' || coalesce(string_agg(value,' '),'')) INTO haystack
    FROM jsonb_each_text(meta);
    haystack := haystack || ' ' || replace(haystack,'gruen','grün');
    FOREACH term IN ARRAY coalesce(terms,ARRAY[]::text[]) LOOP
        IF strpos(haystack,term)=0 THEN RETURN false; END IF;
    END LOOP;
    IF km_min IS NOT NULL THEN
        BEGIN
            meter := round((meta->>'km')::numeric*1000);
        EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
            RETURN false;
        END;
        IF meter IS NULL OR NOT (meter >= km_min AND meter <= km_max) THEN RETURN false; END IF;
    END IF;
    RETURN true;
END;
$$;
