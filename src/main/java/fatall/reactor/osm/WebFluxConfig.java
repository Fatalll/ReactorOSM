package fatall.reactor.osm;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
@EnableWebFlux
@ComponentScan("fatall.reactor.osm")
public class WebFluxConfig {
    @Bean
    public RouterFunction<ServerResponse> peopleRoutes(@NotNull TileHandler handler) {
        return RouterFunctions.route(GET("/{z}/{x}/{y}"), handler::getTile);
    }
}
