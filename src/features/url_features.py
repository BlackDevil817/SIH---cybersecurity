"""
Shared URL feature extraction — used by BOTH training and inference,
so the two can never silently drift out of sync (a common real-world
bug source: training extracts features one way, inference extracts
them slightly differently, and the model gets garbage input at serve
time without anyone noticing).

Design principles:
- Pure functions of the URL string only. No hardcoded domain lookups,
  no "if domain == X" logic anywhere.
- Every feature is documented with WHY it's useful, not just what it is.
- FEATURE_ORDER is the single source of truth for column order. Both
  training and inference must use this exact list/order.
"""
import re
import math
from collections import Counter
from urllib.parse import urlparse
from typing import Dict

# Documented, well-known abused TLDs (free/very cheap registration -> favored
# by throwaway phishing infrastructure). This is general domain-abuse
# knowledge, not a memorized blocklist of specific malicious domains.
SUSPICIOUS_TLDS = {
    "tk", "ml", "ga", "cf", "gq", "top", "xyz", "online",
    "club", "digital", "win", "review", "loan", "download", "site",
}

# Broad phishing-associated keyword list (not tied to any specific brand).
SUSPICIOUS_KEYWORDS = [
    "login", "signin", "verify", "secure", "account", "update",
    "confirm", "suspend", "password", "billing", "webscr", "banking",
]

# Common URL shortener domains used to obscure destination — a legitimate,
# well-documented signal (the shortener itself isn't proof of phishing,
# but it is a real risk signal worth surfacing).
SHORTENER_DOMAINS = {
    "bit.ly", "tinyurl.com", "goo.gl", "t.co", "is.gd", "ow.ly",
    "buff.ly", "rebrand.ly", "cutt.ly",
}

IP_PATTERN = re.compile(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$")

# Single source of truth for feature order. Training and inference
# must both import this list rather than hardcoding column order.
FEATURE_ORDER = [
    "url_length", "domain_length", "path_length",
    "num_dots", "num_hyphens", "num_digits",
    "uses_https", "has_at_symbol", "has_ip_address",
    "has_login_keyword", "num_subdomains", "has_query",
    "num_query_params", "num_special_chars", "has_encoding",
    "has_double_slash", "is_shortened_url",
    "digit_ratio", "special_char_ratio",
    "domain_entropy", "path_entropy",
    "suspicious_keyword_count", "hostname_hyphen_count",
    "query_length", "max_repeated_char_run", "has_suspicious_tld",
]


def _shannon_entropy(s: str) -> float:
    """
    Character-level Shannon entropy of a string. Higher entropy means
    more randomness in character distribution -- a weak but real signal
    for algorithmically generated domains/paths vs human-chosen ones.
    Returns 0.0 for empty strings.
    """
    if not s:
        return 0.0
    counts = Counter(s)
    length = len(s)
    return -sum((c / length) * math.log2(c / length) for c in counts.values())


def _max_repeated_char_run(s: str) -> int:
    """Longest run of the same character repeated consecutively."""
    if not s:
        return 0
    max_run = 1
    current_run = 1
    for i in range(1, len(s)):
        if s[i] == s[i - 1]:
            current_run += 1
            max_run = max(max_run, current_run)
        else:
            current_run = 1
    return max_run


def extract_url_features(url: str) -> Dict[str, float]:
    """
    Extract a fixed-order feature dict from a single URL string.
    Pure function: same URL always produces the same features,
    with no external state or hardcoded domain exceptions.
    
    Normalizes the hostname (strips leading 'www.' and converts to lowercase)
    to eliminate crawl-bias artifacts across web-crawled datasets.
    """
    url = str(url).strip()

    # Ensure a scheme exists so urlparse behaves consistently.
    parse_target = url if "://" in url else f"http://{url}"
    parsed = urlparse(parse_target)

    # 1. Normalize hostname: strip leading 'www.' and lowercase
    raw_hostname = (parsed.hostname or "").lower()
    hostname = re.sub(r"^www\.", "", raw_hostname)

    # 2. Normalize path: treat root '/' identical to bare domain
    path = parsed.path or ""
    if path == "/":
        path = ""
    query = parsed.query or ""

    # 3. Reconstruct canonical URL for whole-string lexical calculations
    netloc = hostname if not parsed.port else f"{hostname}:{parsed.port}"
    norm_url = f"{parsed.scheme}://{netloc}{path}"
    if query:
        norm_url += f"?{query}"

    domain_parts = hostname.split(".") if hostname else []
    # crude TLD guess: last part, if it looks alphabetic
    tld = domain_parts[-1].lower() if domain_parts and domain_parts[-1].isalpha() else ""

    num_digits = sum(c.isdigit() for c in norm_url)
    special_chars = sum(1 for c in norm_url if not c.isalnum() and c not in "./:-")

    features = {
        "url_length": len(norm_url),
        "domain_length": len(hostname),
        "path_length": len(path),
        "num_dots": norm_url.count("."),
        "num_hyphens": norm_url.count("-"),
        "num_digits": num_digits,
        "uses_https": 1 if parsed.scheme == "https" else 0,
        "has_at_symbol": 1 if "@" in norm_url else 0,
        "has_ip_address": 1 if IP_PATTERN.match(hostname) else 0,
        "has_login_keyword": 1 if "login" in norm_url.lower() else 0,
        "num_subdomains": max(len(domain_parts) - 2, 0) if len(domain_parts) > 2 else 0,
        "has_query": 1 if query else 0,
        "num_query_params": query.count("&") + 1 if query else 0,
        "num_special_chars": special_chars,
        "has_encoding": 1 if "%" in norm_url else 0,
        "has_double_slash": 1 if norm_url.count("//") > 1 else 0,
        "is_shortened_url": 1 if any(s in hostname for s in SHORTENER_DOMAINS) else 0,

        "digit_ratio": num_digits / len(norm_url) if norm_url else 0.0,
        "special_char_ratio": special_chars / len(norm_url) if norm_url else 0.0,
        "domain_entropy": _shannon_entropy(hostname),
        "path_entropy": _shannon_entropy(path),
        "suspicious_keyword_count": sum(
            1 for kw in SUSPICIOUS_KEYWORDS if kw in norm_url.lower()
        ),
        "hostname_hyphen_count": hostname.count("-"),
        "query_length": len(query),
        "max_repeated_char_run": _max_repeated_char_run(hostname),
        "has_suspicious_tld": 1 if tld in SUSPICIOUS_TLDS else 0,
    }

    # Guarantee output always matches FEATURE_ORDER exactly.
    return {name: features[name] for name in FEATURE_ORDER}

if __name__ == "__main__":
    # Quick manual sanity check across a mix of legit / phishing / edge cases
    test_urls = [
        "https://google.com",
        "https://www.google.com",
        "https://example.com",
        "https://example.com/login",
        "http://192.168.1.10/verify/account",
        "https://secure-login-example.com/verify",
        "https://steamcornrnurnity.com",
        "https://is.gd/N19JOz",
        "https://tommyhilfigersale.top",
    ]
    for u in test_urls:
        feats = extract_url_features(u)
        print(f"\n{u}")
        for k, v in feats.items():
            print(f"   {k:28s} {v}")