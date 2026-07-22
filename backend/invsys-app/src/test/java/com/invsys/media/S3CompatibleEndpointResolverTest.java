package com.invsys.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class S3CompatibleEndpointResolverTest {

    @Test
    void awsUsesDefaultEndpointWithoutOverride() {
        MediaStorageProperties props = new MediaStorageProperties();
        props.setProvider("AWS");
        props.setEndpoint("");
        props.setPathStyleAccess(false);
        var resolved = S3CompatibleEndpointResolver.resolve(props);
        assertThat(resolved.endpoint()).isEmpty();
        assertThat(resolved.pathStyleAccess()).isFalse();
    }

    @Test
    void gcpAndDigitalOceanAndMinioPresets() {
        MediaStorageProperties gcp = new MediaStorageProperties();
        gcp.setProvider("GCP");
        gcp.setEndpoint("");
        assertThat(S3CompatibleEndpointResolver.resolve(gcp).endpoint())
                .contains("https://storage.googleapis.com");

        MediaStorageProperties ocean = new MediaStorageProperties();
        ocean.setProvider("DIGITALOCEAN");
        ocean.setRegion("sfo3");
        ocean.setEndpoint("");
        assertThat(S3CompatibleEndpointResolver.resolve(ocean).endpoint())
                .contains("https://sfo3.digitaloceanspaces.com");

        MediaStorageProperties oceanBlue = new MediaStorageProperties();
        oceanBlue.setProvider("OCEANBLUE");
        oceanBlue.setRegion("nyc3");
        oceanBlue.setEndpoint("");
        assertThat(S3CompatibleEndpointResolver.resolve(oceanBlue).endpoint())
                .contains("https://nyc3.digitaloceanspaces.com");

        MediaStorageProperties minio = new MediaStorageProperties();
        minio.setProvider("MINIO");
        minio.setEndpoint("");
        var minioResolved = S3CompatibleEndpointResolver.resolve(minio);
        assertThat(minioResolved.endpoint()).contains("http://localhost:9000");
        assertThat(minioResolved.pathStyleAccess()).isTrue();
    }

    @Test
    void explicitEndpointWins() {
        MediaStorageProperties props = new MediaStorageProperties();
        props.setProvider("AWS");
        props.setEndpoint("https://s3.example.local");
        props.setPathStyleAccess(true);
        var resolved = S3CompatibleEndpointResolver.resolve(props);
        assertThat(resolved.endpoint()).contains("https://s3.example.local");
        assertThat(resolved.pathStyleAccess()).isTrue();
    }
}
