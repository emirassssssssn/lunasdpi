package com.lunasdev.lunasdpi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainValidatorTest {
    @Test
    fun acceptsPlainDomain() {
        assertThat(DomainValidator.isValidPattern("discord.com")).isTrue()
        assertThat(DomainValidator.normalize(" Discord.COM. ")).isEqualTo("discord.com")
    }

    @Test
    fun acceptsWildcard() {
        assertThat(DomainValidator.isValidPattern("*.discord.com")).isTrue()
    }

    @Test
    fun rejectsUrlsAndPaths() {
        assertThat(DomainValidator.isValidPattern("https://discord.com")).isFalse()
        assertThat(DomainValidator.isValidPattern("discord.com/path")).isFalse()
        assertThat(DomainValidator.isValidPattern("http://example.com/test")).isFalse()
        assertThat(DomainValidator.rejectReason("https://discord.com")).contains("URL")
    }

    @Test
    fun rejectsSingleLabel() {
        assertThat(DomainValidator.isValidPattern("discord")).isFalse()
        assertThat(DomainValidator.isValidPattern("*")).isFalse()
    }
}
