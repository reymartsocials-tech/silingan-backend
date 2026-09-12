FROM quay.io/keycloak/keycloak:26.5.6 as builder

WORKDIR /opt/keycloak

COPY themes /opt/keycloak/themes
COPY providers /opt/keycloak/providers
COPY import /opt/keycloak/data/import

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.5.6

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]