# Obraz serwera Pixel. Najpierw zbuduj serwer: ./gradlew :server:installDist
# potem z katalogu glownego repozytorium: docker build -f server/Dockerfile -t pixel-server .
# Uruchomienie: docker run -d --name pixel -p 7171:7171 -v pixel-data:/data --restart unless-stopped pixel-server
# W /data serwer trzyma konta (skroty hasel), zapisy postaci, gildie, certyfikat TLS i kopie zapasowe - chron ten wolumen.
FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 7171 --home-dir /data --shell /usr/sbin/nologin pixel \
    && mkdir -p /data && chown pixel /data
COPY server/build/install/server /opt/pixel-server

USER pixel
VOLUME /data
EXPOSE 7171
ENV JAVA_OPTS="-Xmx512m"
ENTRYPOINT ["/opt/pixel-server/bin/server", "7171", "/data"]
