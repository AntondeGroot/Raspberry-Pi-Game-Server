package ADG.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Sprite-sheet images — filename is static (no content hash), so use a
        // week-long TTL rather than "forever".  If the images are ever replaced,
        // either rename them or append a query-string version to bust the cache.
        registry.addResourceHandler("/profilepics*.png", "/robots.png")
                .addResourceLocations("classpath:/public/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());

        // GWT compiled modules: the filename contains a content hash, so they
        // are safe to cache indefinitely with the `immutable` directive.
        registry.addResourceHandler("/app/*.cache.js", "/app/*.cache.html")
                .addResourceLocations("classpath:/launcherDir/app/", "classpath:/public/app/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                        .cachePublic().immutable());

        // GWT bootstrap must never be cached: it selects the right compiled
        // permutation at runtime, so a stale copy would serve the wrong JS.
        // Note: Spring extracts the filename relative to the pattern prefix (/app/),
        // so the resource location must include the app/ subdirectory.
        registry.addResourceHandler("/app/*.nocache.js")
                .addResourceLocations("classpath:/launcherDir/app/", "classpath:/public/app/")
                .setCacheControl(CacheControl.noStore());

        // HTML entry-point must also be uncached so the browser always fetches
        // the latest script tags (index.html is at the root of public/).
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/public/")
                .setCacheControl(CacheControl.noStore());
    }
}