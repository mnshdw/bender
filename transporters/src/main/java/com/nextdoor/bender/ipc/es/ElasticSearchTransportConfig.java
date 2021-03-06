/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Copyright 2017 Nextdoor.com, Inc
 *
 */

package com.nextdoor.bender.ipc.es;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDefault;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription;
import com.nextdoor.bender.auth.AuthConfig;
import com.nextdoor.bender.ipc.http.AbstractHttpTransportConfig;

@JsonTypeName("ElasticSearch")
@JsonSchemaDescription("Writes to an ElasticSearch cluster. When using AWS hosted ES do not "
    + "use gzip compression.")
public class ElasticSearchTransportConfig extends AbstractHttpTransportConfig {

  @JsonSchemaDescription("Authentication scheme.")
  @JsonProperty(required = false)
  private AuthConfig<?> authConfig = null;

  @JsonSchemaDescription("ElasticSearch HTTP endpoint port.")
  @JsonSchemaDefault(value = "9200")
  @JsonProperty(required = false)
  @Min(1)
  @Max(65535)
  private Integer port = 9200;

  @JsonSchemaDescription("Index to write to.")
  @JsonProperty(required = true)
  private String index;

  @JsonSchemaDescription("ElasticSearch document type.")
  @JsonProperty(required = true)
  private String documentType;

  @JsonSchemaDescription("ElasticSearch bulk api path including leading slash '/'.")
  @JsonSchemaDefault(value = "/_bulk")
  @JsonProperty(required = false)
  private String bulkApiPath = "/_bulk";

  @JsonSchemaDescription("Java time format to append to index name.")
  @JsonProperty(required = false)
  private String indexTimeFormat = null;

  @JsonSchemaDescription("Use hash id generated by Bender as document id.")
  @JsonSchemaDefault(value = "false")
  @JsonProperty(required = false)
  private Boolean useHashId = false;

  @JsonSchemaDescription("Use partitions as a mechanism for routing records to Elasticsearch. "
      + "Routing allows a document to be written to a specific shard within an index. "
      + "Using routing can, if configured correctly, dramatically improve read and/or write "
      + "performance when indexing or searching documents. However, this is largely based "
      + "on your data, data source, and workload. For more details on routing consult:\n"
      + "\nhttps://www.elastic.co/blog/customizing-your-document-routing"
      + "\nhttps://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-routing-field.html"
      + "\n\n"
      + "Partition keys and values will be concatenated by \"=\" and separated by \"/\". If"
      + "your partitions are part1=foo and part2=bar the Elasticsearch \"_routing\" field "
      + "will have the value \"part1=foo/part2=bar\". "
      + "\n\n"
      + "A typical routing strategy is to use the data source context associated with a "
      + "function's invocation. For instance with Kinesis this can be the shard-id and with S3 "
      + "the source file. This approach ensures a single function invocation writes to only one "
      + "shard. However, to avoid hot spots, it's also advised that you have a secondary "
      + "partitioning key with low cardinality. For example bucketing by 5 minute intervals "
      + "in addition to using shard-id.")
  @JsonSchemaDefault(value = "false")
  @JsonProperty(required = false)
  private Boolean usePartitionsForRouting = false;

  public AuthConfig<?> getAuthConfig() {
    return this.authConfig;
  }

  public void setAuthConfig(AuthConfig<?> authConfig) {
    this.authConfig = authConfig;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getIndexTimeFormat() {
    return indexTimeFormat;
  }

  public void setIndexTimeFormat(String indexTimeFormat) {
    this.indexTimeFormat = indexTimeFormat;
  }

  @JsonProperty("use_hashid")
  public Boolean isUseHashId() {
    return useHashId;
  }

  @JsonProperty("use_hashid")
  public void setUseHashId(Boolean useHashId) {
    this.useHashId = useHashId;
  }

  @JsonProperty("use_partitions_for_routing")
  public Boolean isUsePartitionsForRouting() {
    return this.usePartitionsForRouting;
  }

  @JsonProperty("use_partitions_for_routing")
  public void setUsePartitionsForRouting(Boolean usePartitionsForRouting) {
    this.usePartitionsForRouting = usePartitionsForRouting;
  }

  public String getBulkApiPath() {
    return this.bulkApiPath;
  }

  public void setBulkApiPath(String bulkApiPath) {
    this.bulkApiPath = bulkApiPath;
  }

  @Override
  public Class<?> getFactoryClass() {
    return ElasticSearchTransportFactory.class;
  }

}
