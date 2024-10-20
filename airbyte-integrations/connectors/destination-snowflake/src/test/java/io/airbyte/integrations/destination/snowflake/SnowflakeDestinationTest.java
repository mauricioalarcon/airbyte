/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.snowflake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airbyte.commons.jackson.MoreMappers;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.resources.MoreResources;
import io.airbyte.integrations.destination.snowflake.SnowflakeDestination.DestinationType;
import io.airbyte.protocol.models.v0.ConnectorSpecification;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SnowflakeDestinationTest {

  private static final ObjectMapper mapper = MoreMappers.initMapper();

  private static Stream<Arguments> urlsDataProvider() {
    return Stream.of(
        arguments("ab12345.us-east-2.aws.snowflakecomputing.com", true),

        arguments("example.snowflakecomputing.com/path/to/resource", false),
        arguments("example.snowflakecomputing.com:8080", false),
        arguments("example.snowflakecomputing.com:12345", false),
        arguments("example.snowflakecomputing.com//path/to/resource", false),
        arguments("example.snowflakecomputing.com/path?query=string", false),
        arguments("example.snowflakecomputing.com/#fragment", false),
        arguments("ab12345.us-east-2.aws.snowflakecomputing. com", false),
        arguments("ab12345.us-east-2.aws.snowflakecomputing..com", false),
        arguments("www.ab12345.us-east-2.aws.snowflakecomputing.com", false),
        arguments("http://ab12345.us-east-2.aws.snowflakecomputing.com", false),
        arguments("https://ab12345.us-east-2.aws.snowflakecomputing.com", false));
  }

  @ParameterizedTest
  @MethodSource({"urlsDataProvider"})
  void testUrlPattern(final String url, final boolean isMatch) throws Exception {
    final ConnectorSpecification spec = new SnowflakeDestination(OssCloudEnvVarConsts.AIRBYTE_OSS).spec();
    final Pattern pattern = Pattern.compile(spec.getConnectionSpecification().get("properties").get("host").get("pattern").asText());

    Matcher matcher = pattern.matcher(url);
    assertEquals(isMatch, matcher.find());
  }

  @Test
  @DisplayName("When given S3 credentials should use COPY")
  public void useS3CopyStrategyTest() {
    final var stubLoadingMethod = mapper.createObjectNode();
    stubLoadingMethod.put("s3_bucket_name", "fake-bucket");
    stubLoadingMethod.put("access_key_id", "test");
    stubLoadingMethod.put("secret_access_key", "test key");

    final var stubConfig = mapper.createObjectNode();
    stubConfig.set("loading_method", stubLoadingMethod);

    assertTrue(SnowflakeDestinationResolver.isS3Copy(stubConfig));
  }

  @Test
  @DisplayName("When given GCS credentials should use COPY")
  public void useGcsCopyStrategyTest() {
    final var stubLoadingMethod = mapper.createObjectNode();
    stubLoadingMethod.put("project_id", "my-project");
    stubLoadingMethod.put("bucket_name", "my-bucket");
    stubLoadingMethod.put("credentials_json", "hunter2");

    final var stubConfig = mapper.createObjectNode();
    stubConfig.set("loading_method", stubLoadingMethod);

    assertTrue(SnowflakeDestinationResolver.isGcsCopy(stubConfig));
  }

  @Test
  @DisplayName("When not given S3 credentials should use INSERT")
  public void useInsertStrategyTest() {
    final var stubLoadingMethod = mapper.createObjectNode();
    final var stubConfig = mapper.createObjectNode();
    stubConfig.set("loading_method", stubLoadingMethod);
    assertFalse(SnowflakeDestinationResolver.isS3Copy(stubConfig));
  }

  @ParameterizedTest
  @MethodSource("destinationTypeToConfig")
  public void testS3ConfigType(final String configFileName, final DestinationType expectedDestinationType) throws Exception {
    final JsonNode config = Jsons.deserialize(MoreResources.readResource(configFileName), JsonNode.class);
    final DestinationType typeFromConfig = SnowflakeDestinationResolver.getTypeFromConfig(config);
    assertEquals(expectedDestinationType, typeFromConfig);
  }

  private static Stream<Arguments> destinationTypeToConfig() {
    return Stream.of(
        arguments("copy_gcs_config.json", DestinationType.COPY_GCS),
        arguments("copy_s3_config.json", DestinationType.COPY_S3),
        arguments("insert_config.json", DestinationType.INTERNAL_STAGING));
  }

}
