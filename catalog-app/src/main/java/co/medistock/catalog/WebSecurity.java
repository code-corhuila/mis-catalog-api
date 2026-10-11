package co.medistock.catalog;

import co.medistock.catalog.adapters.Errors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableMethodSecurity
public class WebSecurity {
    @Bean JwtDecoder decoder(@Value("${catalog.jwks-url}") String uri) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(2000);
        var decoder = NimbusJwtDecoder.withJwkSetUri(uri).jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(new RestTemplate(factory)).build();
        decoder.setJwtValidator(JwtValidation.validator());
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, JwtDecoder decoder, Errors errors) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles);
        var resolver = new DefaultBearerTokenResolver();
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/medications").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/medications/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/medications/*/deactivate").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/medications", "/api/v1/medications/**")
                    .hasAnyRole("ADMIN", "INVENTORY", "SUPERVISOR", "CONSULTATION")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource
                .bearerTokenResolver(request -> "/health".equals(request.getRequestURI()) ? null : resolver.resolve(request))
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((request, response, error) -> errors.write(request, response, 401, "UNAUTHORIZED", "Autenticacion requerida.")))
            .exceptionHandling(handler -> handler
                .authenticationEntryPoint((request, response, error) -> errors.write(request, response, 401, "UNAUTHORIZED", "Autenticacion requerida."))
                .accessDeniedHandler((request, response, error) -> errors.write(request, response, 403, "FORBIDDEN", "Permisos insuficientes.")));
        return http.build();
    }
}
