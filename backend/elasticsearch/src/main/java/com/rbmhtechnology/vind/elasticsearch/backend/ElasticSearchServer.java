package com.rbmhtechnology.vind.elasticsearch.backend;

import com.rbmhtechnology.vind.SearchServerException;
import com.rbmhtechnology.vind.api.Document;
import com.rbmhtechnology.vind.api.SearchServer;
import com.rbmhtechnology.vind.api.ServiceProvider;
import com.rbmhtechnology.vind.api.query.FulltextSearch;
import com.rbmhtechnology.vind.api.query.delete.Delete;
import com.rbmhtechnology.vind.api.query.get.RealTimeGet;
import com.rbmhtechnology.vind.api.query.suggestion.ExecutableSuggestionSearch;
import com.rbmhtechnology.vind.api.query.update.Update;
import com.rbmhtechnology.vind.api.result.BeanGetResult;
import com.rbmhtechnology.vind.api.result.BeanSearchResult;
import com.rbmhtechnology.vind.api.result.DeleteResult;
import com.rbmhtechnology.vind.api.result.GetResult;
import com.rbmhtechnology.vind.api.result.IndexResult;
import com.rbmhtechnology.vind.api.result.SearchResult;
import com.rbmhtechnology.vind.api.result.StatusResult;
import com.rbmhtechnology.vind.api.result.SuggestionResult;
import com.rbmhtechnology.vind.configure.SearchConfiguration;
import com.rbmhtechnology.vind.elasticsearch.backend.util.DocumentUtil;
import com.rbmhtechnology.vind.elasticsearch.backend.util.FieldUtil;
import com.rbmhtechnology.vind.model.DocumentFactory;
import org.apache.http.util.Asserts;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class ElasticSearchServer extends SearchServer {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchServer.class);
    private static final Logger elasticClientLogger = LoggerFactory.getLogger(log.getName() + "#elasticSearchClient");

    private ServiceProvider serviceProviderClass;
    private final ElasticVindClient elasticSearchClient;

    public ElasticSearchServer() {
        // this is mainly used with the ServiceLoader infrastructure
        this(getElasticServerProvider() != null ? getElasticServerProvider().getInstance() : null);
        serviceProviderClass = getElasticServerProvider();
    }

    /**
     * Creates an instance of SolrSearch server performing ping and the schema validity check.
     * @param client SolrClient to connect to.
     */
    public ElasticSearchServer(ElasticVindClient client) {
        this(client, true);
    }

    /**
     * Creates an instance of SolrSearch server allowing to avoid the schema validity check.
     * @param client SolrClient to connect to.
     * @param check true to perform local schema validity check against remote schema, false otherwise.
     */
    protected ElasticSearchServer(ElasticVindClient client, boolean check) {
        elasticSearchClient = client;

        //In order to perform unit tests with mocked solrClient, we do not need to do the schema check.
        if(check && client != null) {
            try {
                if (elasticSearchClient.ping()) {
                    log.debug("Successful ping to Elasticsearch server");
                } else {
                    log.error("Cannot connect to Elasticsearch server: ping failed");
                    throw new SearchServerException("Cannot connect to Elasticsearch server: ping failed");
                }
            } catch ( IOException e) {
                log.error("Cannot connect to Elasticsearch server: ping failed");
                throw new SearchServerException("Cannot connect to Elasticsearch server: ping failed", e);
            }

            log.info("Connection to solr server successful");
            checkVersionAndSchema();
        } else {
            log.warn("Solr ping and schema validity check has been deactivated.");
        }
    }

    private void checkVersionAndSchema() {

    }

    @Override
    public Object getBackend() {
        return elasticSearchClient;
    }

    @Override
    public StatusResult getBackendStatus() {
        try {
            if(elasticSearchClient.ping()) {
                return StatusResult.up().setDetail("status", 0);
            } else {
                return StatusResult.down().setDetail("status", 1);
            }

        } catch ( IOException e) {
            log.error("Cannot connect to Elasticsearch server: ping failed");
            throw new SearchServerException("Cannot connect to Elasticsearch server: ping failed", e);
        }
    }

    @Override
    public IndexResult index(Document... docs) {
        Asserts.notNull(docs,"Document to index should not be null.");
        Asserts.check(docs.length > 0, "Should be at least one document to index.");
        return indexMultipleDocuments(Arrays.asList(docs), -1);
    }

    @Override
    public IndexResult index(List<Document> doc) {
        return null;
    }

    @Override
    public IndexResult indexWithin(Document doc, int withinMs) {
        return null;
    }

    @Override
    public IndexResult indexWithin(List<Document> doc, int withinMs) {
        return null;
    }

    @Override
    public DeleteResult delete(Document doc) {
        return null;
    }

    @Override
    public DeleteResult deleteWithin(Document doc, int withinMs) {
        return null;
    }

    @Override
    public boolean execute(Update update, DocumentFactory factory) {
        return false;
    }

    @Override
    public DeleteResult execute(Delete delete, DocumentFactory factory) {
        return null;
    }

    @Override
    public void commit(boolean optimize) {

    }

    @Override
    public <T> BeanSearchResult<T> execute(FulltextSearch search, Class<T> c) {
        return null;
    }

    @Override
    public SearchResult execute(FulltextSearch search, DocumentFactory factory) {
        return null;
    }

    @Override
    public String getRawQuery(FulltextSearch search, DocumentFactory factory) {
        return null;
    }

    @Override
    public <T> String getRawQuery(FulltextSearch search, Class<T> c) {
        return null;
    }

    @Override
    public <T> SuggestionResult execute(ExecutableSuggestionSearch search, Class<T> c) {
        return null;
    }

    @Override
    public SuggestionResult execute(ExecutableSuggestionSearch search, DocumentFactory assets) {
        return null;
    }

    @Override
    public SuggestionResult execute(ExecutableSuggestionSearch search, DocumentFactory assets, DocumentFactory childFactory) {
        return null;
    }

    @Override
    public String getRawQuery(ExecutableSuggestionSearch search, DocumentFactory factory) {
        return null;
    }

    @Override
    public String getRawQuery(ExecutableSuggestionSearch search, DocumentFactory factory, DocumentFactory childFactory) {
        return null;
    }

    @Override
    public <T> String getRawQuery(ExecutableSuggestionSearch search, Class<T> c) {
        return null;
    }

    @Override
    public <T> BeanGetResult<T> execute(RealTimeGet search, Class<T> c) {
        return null;
    }

    @Override
    public GetResult execute(RealTimeGet search, DocumentFactory assets) {
        return null;
    }

    @Override
    public void clearIndex() {

    }

    @Override
    public void close() {

    }

    @Override
    public Class<ServiceProvider> getServiceProviderClass() {
        return null;
    }

    private static ElasticServerProvider getElasticServerProvider() {
        final String providerClassName = SearchConfiguration.get(SearchConfiguration.SERVER_PROVIDER, null);

        final ServiceLoader<ElasticServerProvider> loader = ServiceLoader.load(ElasticServerProvider.class);
        final Iterator<ElasticServerProvider> it = loader.iterator();

        ElasticServerProvider serverProvider = null;
        if(providerClassName == null) {
            if (!it.hasNext()) {
                log.error("No ElasticServerProvider in classpath");
                throw new RuntimeException("No ElasticServerProvider in classpath");
            } else {
                serverProvider = it.next();
            }
            if (it.hasNext()) {
                log.warn("Multiple bindings for ElasticServerProvider found: {}", loader.iterator());
            }
        } else {
            try {
                final Class<?> providerClass = Class.forName(providerClassName);

                while(it.hasNext()) {
                    final ElasticServerProvider p = it.next();
                    if(providerClass.isAssignableFrom(p.getClass())) {
                        serverProvider = p;
                        break;
                    }
                }

            } catch (ClassNotFoundException e) {
                log.warn("Specified class {} is not in classpath",providerClassName, e);
                //throw new RuntimeException("Specified class " + providerClassName + " is not in classpath");
            }
            log.info("No server provider of type class {} found in classpath for server {}", providerClassName, ElasticServerProvider.class.getCanonicalName());
        }

        return serverProvider;
    }

    private IndexResult indexSingleDocument(Document doc, int withinMs) {
        final Instant start = Instant.now();
        final Map<String,Object> document = DocumentUtil.createInputDocument(doc);

        try {
            if (elasticClientLogger.isTraceEnabled()) {
                elasticClientLogger.debug(">>> add({})", doc.getId());
            } else {
                elasticClientLogger.debug(">>> add({})", doc.getId());
            }

            //removeNonParentDocument(doc, withinMs);
            final IndexResponse response = this.elasticSearchClient.add(document);
            return new IndexResult(Instant.from(start).toEpochMilli());

        } catch (ElasticsearchException | IOException e) {
            log.error("Cannot index document {}", document.get(FieldUtil.ID) , e);
            throw new SearchServerException("Cannot index document", e);
        }
    }

    private IndexResult indexMultipleDocuments(List<Document> docs, int withinMs) {
        final Instant start = Instant.now();
        final List<Map<String,Object>> jsonDocs = docs.parallelStream()
                .map(DocumentUtil::createInputDocument)
                .collect(Collectors.toList());
        try {
            if (elasticClientLogger.isTraceEnabled()) {
                elasticClientLogger.debug(">>> add({})", jsonDocs);
            } else {
                elasticClientLogger.debug(">>> add({})", jsonDocs);
            }

            final BulkResponse response =this.elasticSearchClient.add(jsonDocs) ;
            return new IndexResult(Instant.from(start).toEpochMilli());

        } catch (ElasticsearchException | IOException e) {
            log.error("Cannot index documents {}", jsonDocs, e);
            throw new SearchServerException("Cannot index documents", e);
        }
    }
}
