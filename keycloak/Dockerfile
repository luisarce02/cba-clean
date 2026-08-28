FROM quay.io/keycloak/keycloak:26.1

COPY realm/cba-clean-realm.json /opt/keycloak/data/import/cba-clean-realm.json

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--import-realm"]
