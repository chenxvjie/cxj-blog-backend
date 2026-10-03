package com.cxj.blog.media;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosConfiguration {
  public record Settings(boolean enabled, String bucket, String publicBaseUrl) {}

  @Bean Settings cosSettings(@Value("${app.integrations.cos-enabled:false}") boolean enabled,
      @Value("${app.cos.bucket:}") String bucket, @Value("${app.cos.public-base-url:}") String base) {
    if (enabled) {
      URI url = URI.create(base);
      if (!bucket.matches("[a-z0-9-]+-[0-9]+") || !"https".equals(url.getScheme())
          || url.getHost() == null || url.getRawUserInfo() != null || url.getRawQuery() != null
          || url.getRawFragment() != null || !(url.getPath().isEmpty() || url.getPath().equals("/")))
        throw new IllegalStateException("Enabled COS requires a bucket and HTTPS CDN origin URL");
    }
    return new Settings(enabled, bucket, base.replaceAll("/$", ""));
  }

  @Bean(destroyMethod="close") CosGateway cosGateway(Settings settings,
      @Value("${app.cos.region:ap-nanjing}") String region,
      @Value("${app.cos.secret-id:}") String id, @Value("${app.cos.secret-key:}") String key) {
    if (!settings.enabled()) return new CosGateway(null, settings.bucket());
    if (id.isBlank() || key.isBlank() || !region.matches("[a-z]+-[a-z]+(?:-[a-z]+)?"))
      throw new IllegalStateException("Enabled COS requires separate COS credentials and region");
    ClientConfig config = new ClientConfig(new Region(region));
    config.setHttpProtocol(HttpProtocol.https);
    config.setConnectionTimeout(5000);
    config.setSocketTimeout(10000);
    config.setMaxErrorRetry(0);
    return new CosGateway(new COSClient(new BasicCOSCredentials(id, key), config), settings.bucket());
  }
}
