package com.rbmhtechnology.vind.elasticsearch.backend.util;

import com.rbmhtechnology.vind.api.Document;
import com.rbmhtechnology.vind.api.query.update.Update;
import com.rbmhtechnology.vind.api.query.update.UpdateOperation;
import com.rbmhtechnology.vind.model.ComplexFieldDescriptor;
import com.rbmhtechnology.vind.model.DocumentFactory;
import com.rbmhtechnology.vind.model.FieldDescriptor;
import com.rbmhtechnology.vind.model.MultiValueFieldDescriptor;
import com.rbmhtechnology.vind.model.MultiValuedComplexField;
import com.rbmhtechnology.vind.model.value.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rbmhtechnology.vind.api.query.update.Update.UpdateOperations.set;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DocumentUtil {

    private static final Logger log = LoggerFactory.getLogger(DocumentUtil.class);

    public static Map<String, Object> createInputDocument(Document doc) {

        final Map<String, Object> docMap = new HashMap<>();
        //add fields
        doc.listFieldDescriptors()
                .values()
                .stream()
                .filter(doc::hasValue)
                .forEach(descriptor ->
                        doc.getFieldContexts(descriptor)
                                .forEach(context -> {
                                    Optional.ofNullable(FieldUtil.getFieldName(descriptor, context))
                                            .ifPresent( fieldName -> {
                                                Optional.ofNullable( doc.getContextualizedValue(descriptor, context))
                                                        .ifPresent( value -> docMap.put(fieldName, toSolrJType(value)));
                                            });
                                }
                        )
                );

        //TODO: add subdocuments if implemented
        docMap.put(FieldUtil.ID, doc.getId());
        docMap.put(FieldUtil.TYPE, doc.getType());

        return docMap;
    }

    private static Object toSolrJType(Object value) {
        if(value!=null) {
            if(Object[].class.isAssignableFrom(value.getClass())){
                return toSolrJType(Arrays.asList((Object[])value));
            }
            if(Collection.class.isAssignableFrom(value.getClass())){
                final Collection<Object> values = (Collection<Object>) value;
                return values.stream()
                        .map(DocumentUtil::toSolrJType)
                        .collect(Collectors.toList());
            }
            if(value instanceof ZonedDateTime) {
                return Date.from(((ZonedDateTime) value).toInstant());
            }
            if(value instanceof LatLng) {
                return value.toString();
            }
            if(value instanceof Date) {
                //noinspection RedundantCast
                return ((Date) value);
            }
        }
        return value;
    }

    public static Document buildVindDoc(Map<String,Object> docMap, DocumentFactory factory, String searchContext) {

        final Document document = factory.createDoc(String.valueOf(docMap.get(FieldUtil.ID)));

        //TODO: No child documnt search implemented yet
        //            if (childCounts != null) {
        //                document.setChildCount(ObjectUtils.defaultIfNull(childCounts.get(document.getId()), 0));
        //            }

        // Setting score if present in result
        Optional.ofNullable(docMap.get(FieldUtil.SCORE))
                .ifPresent(score -> document.setScore(Float.parseFloat(score.toString())));

        // Setting distance if present in result
        Optional.ofNullable(docMap.get(FieldUtil.DISTANCE))
                .ifPresent(distance -> document.setDistance(Float.parseFloat(distance.toString())));

        docMap.keySet().stream()
            .filter(name -> ! Arrays.asList(FieldUtil.ID, FieldUtil.TYPE, FieldUtil.SCORE, FieldUtil.DISTANCE)
                    .contains(name))
            .forEach(name -> {
                final Object o = docMap.get(name);
                final String contextPrefix = searchContext != null ? searchContext + "_" : "";
                final Matcher internalPrefixMatcher = Pattern.compile(FieldUtil.INTERNAL_FIELD_PREFIX).matcher(name);
                final String contextualizedName = internalPrefixMatcher.replaceFirst("");
                final boolean contextualized = Objects.nonNull(searchContext) && contextualizedName.contains(contextPrefix);
                final String fieldRawName = contextualizedName.replace(contextPrefix, "");
                if (factory.hasField(fieldRawName)) {
                    final FieldDescriptor<?> field = factory.getField(fieldRawName);
                    Class<?> type;
                    if (ComplexFieldDescriptor.class.isAssignableFrom(field.getClass())) {
                        type = ((ComplexFieldDescriptor) field).getStoreType();
                    } else {
                        type = field.getType();
                    }
                    try {
                        if (o instanceof Collection) {
                            final Collection<Object> solrValues = new ArrayList<>();
                            if (ZonedDateTime.class.isAssignableFrom(type)) {
                                ((Collection<?>) o).forEach(ob -> solrValues.add(ZonedDateTime.ofInstant(((Date) ob).toInstant(), ZoneId.of("UTC"))));
                            } else if (Date.class.isAssignableFrom(type)) {
                                ((Collection<?>) o).forEach(ob -> solrValues.add(DateTimeFormatter.ISO_INSTANT.format(((Date) ob).toInstant())));
                            } else if (LatLng.class.isAssignableFrom(type)) {
                                ((Collection<?>) o).forEach(ob -> {
                                    try {
                                        solrValues.add(LatLng.parseLatLng(ob.toString()));
                                    } catch (ParseException e) {
                                        log.error("Unable to parse solr result field '{}' value '{}' to field descriptor type [{}]",
                                                fieldRawName, o.toString(), type);
                                        throw new RuntimeException(e);
                                    }
                                });
                            } else {
                                solrValues.addAll((Collection<Object>) o);
                            }

                            if (ComplexFieldDescriptor.class.isAssignableFrom(field.getClass())) {
                                if (contextualized) {
                                    document.setContextualizedValues((MultiValuedComplexField<Object, ?, ?>) field, searchContext, solrValues);
                                } else {
                                    document.setValues((MultiValuedComplexField<Object, ?, ?>) field, solrValues);
                                }

                            } else {
                                if (contextualized) {
                                    document.setContextualizedValues((MultiValueFieldDescriptor<Object>) field, searchContext, solrValues);
                                } else {
                                    document.setValues((MultiValueFieldDescriptor<Object>) field, solrValues);
                                }
                            }

                        } else {
                            Object storedValue;
                            if (ZonedDateTime.class.isAssignableFrom(type)) {
                                storedValue = ZonedDateTime.ofInstant(((Date) o).toInstant(), ZoneId.of("UTC"));
                            } else if (Date.class.isAssignableFrom(type)) {
                                storedValue = (Date) o;
                            } else if (LatLng.class.isAssignableFrom(type)) {
                                storedValue = LatLng.parseLatLng(o.toString());
                            } else {
                                storedValue = castForDescriptor(o, field, FieldUtil.Fieldname.UseCase.Stored);
                            }
                            if (contextualized) {
                                document.setContextualizedValue((FieldDescriptor<Object>) field, searchContext, storedValue);
                            } else {
                                document.setValue((FieldDescriptor<Object>) field, storedValue);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Unable to parse solr result field '{}' value '{}' to field descriptor type [{}]",
                                fieldRawName, o.toString(), type);
                        throw new RuntimeException(e);
                    }
                }
            });
        return document;
    }

    private static Object castForDescriptor(String s, FieldDescriptor<?> descriptor, FieldUtil.Fieldname.UseCase useCase) {

        Class<?> type;
        if(Objects.nonNull(descriptor)) {
            if (ComplexFieldDescriptor.class.isAssignableFrom(descriptor.getClass())) {
                switch (useCase) {
                    case Facet:
                        type = ((ComplexFieldDescriptor) descriptor).getFacetType();
                        break;
                    case Stored:
                        type = ((ComplexFieldDescriptor) descriptor).getStoreType();
                        break;
                    case Suggest: type = String.class;
                        break;
                    case Filter: type = ((ComplexFieldDescriptor)descriptor).getFacetType();
                        break;
                    default:
                        type = descriptor.getType();
                }
            } else {
                type = descriptor.getType();
            }

            return castForDescriptor(s, type);
        } else return s;

    }

    private static Object castForDescriptor(String s, FieldDescriptor<?> descriptor) {

        return castForDescriptor(s,descriptor.getType());
    }

    private static Object castForDescriptor(String s, Class<?> type) {

        if(Long.class.isAssignableFrom(type)) {
            return Long.valueOf(s);
        }
        if(Integer.class.isAssignableFrom(type)) {
            return Integer.valueOf(s);
        }
        if(Double.class.isAssignableFrom(type)) {
            return Double.valueOf(s);
        }
        if(Number.class.isAssignableFrom(type)) {
            return Float.valueOf(s);
        }
        if(Boolean.class.isAssignableFrom(type)) {
            return Boolean.valueOf(s);
        }
        if(ZonedDateTime.class.isAssignableFrom(type)) {
            return ZonedDateTime.parse(s);
        }
        if(Date.class.isAssignableFrom(type)) {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(Long.valueOf(s)));
        }
        if(ByteBuffer.class.isAssignableFrom(type)) {
            return ByteBuffer.wrap(s.getBytes(UTF_8));
        }
        return s;
    }

    private static Object castForDescriptor(Object o, FieldDescriptor<?> descriptor, FieldUtil.Fieldname.UseCase useCase) {

        Class<?> type;

        if (ComplexFieldDescriptor.class.isAssignableFrom(descriptor.getClass())){
            switch (useCase) {
                case Facet: type = ((ComplexFieldDescriptor)descriptor).getFacetType();
                    break;
                case Stored: type = ((ComplexFieldDescriptor)descriptor).getStoreType();
                    break;
                default: type = descriptor.getType();
            }
        } else {
            type = descriptor.getType();
        }

        if(o != null){
            if(Collection.class.isAssignableFrom(o.getClass())) {
                return ((Collection)o).stream()
                        .map( element -> castForDescriptor(element,descriptor))
                        .collect(Collectors.toList());
            }
            return castForDescriptor(o,type);
        }
        return o;
    }

    private static Object castForDescriptor(Object o, FieldDescriptor<?> descriptor) {

        Class<?> type = descriptor.getType();

        if(o != null){
            if(Collection.class.isAssignableFrom(o.getClass())) {
                return ((Collection)o).stream()
                        .map( element -> castForDescriptor(element,descriptor))
                        .collect(Collectors.toList());
            }
            return castForDescriptor(o,type);
        }
        return o;
    }

    private static Object castForDescriptor(Object o, Class<?> type) {

        if(o != null){

            if(Long.class.isAssignableFrom(type)) {
                return ((Number)o).longValue();
            }
            if(Integer.class.isAssignableFrom(type)) {
                return ((Number)o).intValue();
            }
            if(Double.class.isAssignableFrom(type)) {
                return ((Number)o).doubleValue();
            }
            if(Number.class.isAssignableFrom(type)) {
                return ((Number)o).floatValue();
            }
            if(Boolean.class.isAssignableFrom(type)) {
                return (Boolean) o;
            }
            if(ZonedDateTime.class.isAssignableFrom(type)) {
                if(o instanceof Date){
                    return ZonedDateTime.ofInstant(((Date) o).toInstant(), ZoneId.of("UTC"));
                }
                return (ZonedDateTime) o;
            }
            if(Date.class.isAssignableFrom(type)) {
                return (Date) o;
            }
            if(ByteBuffer.class.isAssignableFrom(type)) {
                return ByteBuffer.wrap(new String((byte[]) o).getBytes()) ;
            }
        }
        return o;
    }

    public static Map<String,  Map<String, Object>> getUpdateDocument(Update update, String type) {

        final Map<String, Object> sdoc = new HashMap<>();
        sdoc.put(FieldUtil.ID, update.getId());
        sdoc.put(FieldUtil.TYPE, type);

        log.debug("Atomic Update - Mapping the Vind update operations to a map document with ID [{}].", update.getId());
        final HashMap<FieldDescriptor<?>, HashMap<String, SortedSet<UpdateOperation>>> updateOptions = update.getOptions();

        log.debug("Atomic Update - Updating {} fields.", updateOptions.keySet().size());
        updateOptions.keySet()
                .forEach(fieldDescriptor -> {
                            log.debug("Atomic Update - Updating {} different contexts for field [{}].", updateOptions.get(fieldDescriptor).keySet().size(), fieldDescriptor);
                            updateOptions.get(fieldDescriptor).keySet()
                                    .forEach(context -> {
                                        //NOTE: Backwards compatibility
                                        final String updateContext = Objects.isNull(context)? update.getUpdateContext() : context;
                                        final String fieldName = FieldUtil.getFieldName(fieldDescriptor, updateContext);
                                        if (fieldName != null) {
                                            final Map<String, Object> fieldModifiers = new HashMap<>();
                                            updateOptions.get(fieldDescriptor).get(context).forEach(entry -> {
                                                Update.UpdateOperations opType = entry.getType();
                                                fieldModifiers.put(opType.name(), toSolrJType(entry.getValue()));
                                            });
                                            sdoc.put(fieldName, fieldModifiers);
                                        }
                                    }
                            );
                        }
                );
        return sdoc;

    }
}