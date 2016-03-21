/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014-2016 Center for Open Middleware.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Artifact    : org.ldp4j.commons:ldp4j-commons-http:0.3.0-SNAPSHOT
 *   Bundle      : ldp4j-commons-http-0.3.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.http;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

final class ContentNegotiation {

	interface Parser<T> {

		T parse(String data);

	}

	private static final class CharsetParser implements Parser<Charset> {

		@Override
		public Charset parse(final String data) {
			if("*".equals(data)) {
				return null;
			}
			try {
				return Charset.forName(data);
			} catch (final IllegalCharsetNameException e) {
				throw new IllegalArgumentException("Invalid charset: illegal charset name ('"+data+"')",e);
			} catch (final UnsupportedCharsetException e) {
				throw new IllegalArgumentException("Invalid charset: not supported ('"+data+"')",e);
			}
		}

	}

	/**
	 * Parser capable of producing {@code Locale} instances for valid
	 * {@code language-range} definitions.
	 *
	 * @see <a href="http://tools.ietf.org/html/rfc4674#section-2.1">RFC 4674 - Section 2.1</a>
	 * @see <a href="http://tools.ietf.org/html/rfc7231#section-5.3.5">RFC 7231 - Section 5.3.5</a>
	 */
	private static final class LocaleParser implements Parser<Locale> {

		private static final class Requirements {

			private final String type;
			private final String composition;
			private final CharMatcher matcher;

			Requirements(final String type,final String composition,final CharMatcher matcher) {
				this.type = type;
				this.composition = composition;
				this.matcher = matcher;

			}

			private boolean hasValidLength(final String tag) {
				return tag.length()>0 && tag.length()<9;
			}

			String verify(final String tag) {
				checkArgument(
					this.matcher.matchesAllOf(tag),
					"Invalid language-range: %s must be %s ('%s')",
					this.type,
					this.composition,
					tag);
				checkArgument(
					hasValidLength(tag),
					"Invalid language-range: %s length must be greater than 1 and lower than 9 ('%s' --> %s)",
					this.type,
					tag,
					tag.length());
				return tag;
			}
		}

		private static final Requirements[] REQUIREMENTS={
			new Requirements("primary tag","alphabetical",CharMatcher.JAVA_LETTER),
			new Requirements("subtag","alphanumerical",CharMatcher.JAVA_LETTER_OR_DIGIT)
		};

		private static final String WILDCARD = "*";

		@Override
		public Locale parse(final String locale) {
			if(WILDCARD.equals(locale)) {
				return null;
			}
			final List<String> tags=getTags(locale);
			checkArgument(tags.size()<3,"Invalid language-range: to many tags (%s)",tags.size());
			return
				new Locale(
					verifyTag(tags,0),
					verifyTag(tags,1));
		}

		private List<String> getTags(final String locale) {
			final List<String> tags=Lists.newArrayList();
			final int end = locale.length();
			int start=0;
			do {
				final int next=locale.indexOf('-',start);
				String tag=null;
				if(next<0) {
					tag=locale.substring(start,end);
					start=end;
				} else {
					tag=locale.substring(start,next);
					start=next+1;
				}
				tags.add(tag);
			} while(start<end);
			return tags;
		}

		private String verifyTag(final List<String> parts, final int index) {
			if(parts.size()<=index) {
				return "";
			}
			final Requirements requirement=REQUIREMENTS[index];
			return
				requirement.
					verify(parts.get(index));
		}
	}

	private static final Pattern QUALITY_PATTERN = Pattern.compile("[ \t]*;[ \t]*[qQ]=((1\\.0{0,3})|(0\\.\\d{0,3}))");

	private ContentNegotiation() {
	}

	private static <T> Weighted<T> parseWith(final String candidate, final Parser<T> parser) {
		final Matcher matcher = QUALITY_PATTERN.matcher(candidate);
		final List<String> weights=Lists.newArrayList();
		while(matcher.find()) {
			final String group = matcher.group(1);
			weights.add(group);
		}
		checkArgument(weights.size()<2,"Only one quality value can be specified (found %s: %s)",weights.size(),Joiner.on(", ").join(weights));
		final String trimmed = matcher.replaceAll("");
		return
			Weighted.
				newInstance().
					weight(Double.parseDouble(Iterables.getFirst(weights,"1.000"))).
					content(parser.parse(trimmed));
	}

	static Weighted<Charset> acceptCharset(final String header) {
		return parseWith(header, new CharsetParser());
	}

	static Weighted<Locale> acceptLanguage(final String header) {
		return parseWith(header, new LocaleParser());
	}

}