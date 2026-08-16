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
    void rejectsMetadataAndLoopbackUnlessMinio() {
        MediaStorageProperties aws = new MediaStorageProperties();
        aws.setProvider("AWS");
        aws.setAllowPrivateEndpoints(false);
        aws.setEndpoint("http://169.254.169.254/latest/meta-data");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> S3CompatibleEndpointResolver.resolve(aws))
                .isInstanceOf(com.invsys.core.common.ApiException.class);

        MediaStorageProperties loop = new MediaStorageProperties();
        loop.setProvider("AWS");
        loop.setAllowPrivateEndpoints(false);
        loop.setEndpoint("http://127.0.0.1:9000");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> S3CompatibleEndpointResolver.resolve(loop))
                .isInstanceOf(com.invsys.core.common.ApiException.class);

        MediaStorageProperties minio = new MediaStorageProperties();
        minio.setProvider("MINIO");
        minio.setEndpoint("http://127.0.0.1:9000");
        assertThat(S3CompatibleEndpointResolver.resolve(minio).endpoint()).contains("http://127.0.0.1:9000");
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
