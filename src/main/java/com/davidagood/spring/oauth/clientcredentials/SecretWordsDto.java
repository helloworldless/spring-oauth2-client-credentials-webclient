package com.davidagood.spring.oauth.clientcredentials;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class SecretWordsDto {

	private final List<String> words;

	private final Instant createdAt;

	private SecretWordsDto(List<String> words, Instant createdAt) {
		this.words = words;
		this.createdAt = createdAt;
	}

	public static SecretWordsDto from(List<String> words, Instant createdAt) {
		return new SecretWordsDto(words, createdAt);
	}

	public List<String> getWords() {
		return words;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public String toString() {
		return "SecretWordsDto{" + "words=" + words + ", createdAt=" + createdAt + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		SecretWordsDto that = (SecretWordsDto) o;
		return words.equals(that.words) && createdAt.equals(that.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(words, createdAt);
	}

}
