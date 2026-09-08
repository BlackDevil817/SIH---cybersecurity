"""
Step 2 — augment the legitimate (Labels=0) class with real, well-known
domains in bare-root / simple form, to fix a structural gap in the
original dataset: legitimate URLs were all crawled *pages* (www + path),
while phishing URLs include many bare-root domains. This left the
model with literally zero legitimate bare-root examples to learn from.

This is NOT a hardcoded exception for specific URLs at inference time.
It is training data augmentation: we are teaching the model that
"no path" is not itself a phishing signal, using a broad, diverse
set of independently-known-legitimate domains.

Output is saved SEPARATELY from the original dataset and tagged with
a `source` column, so real vs synthetic/augmented data is never
silently conflated in reporting.
"""
import os
import pandas as pd

ORIGINAL_PATH = os.path.join("training", "dataset", "data_bal - 20000.xlsx")
OUTPUT_PATH = os.path.join("training", "dataset", "data_augmented.csv")

# Curated list of well-known, independently-verifiable legitimate domains.
# Deliberately diverse: multiple sectors, multiple countries, multiple TLDs,
# so the model can't just learn ".com => safe" as a new artifact.
LEGIT_DOMAINS = [
    # Big tech / search / cloud
    "google.com", "microsoft.com", "apple.com", "amazon.com", "meta.com",
    "netflix.com", "adobe.com", "salesforce.com", "oracle.com", "ibm.com",
    "cisco.com", "intel.com", "nvidia.com", "dropbox.com", "slack.com",
    "zoom.us", "spotify.com", "wikipedia.org", "mozilla.org", "github.com",
    "gitlab.com", "stackoverflow.com", "atlassian.com", "linkedin.com",
    "twitter.com", "x.com", "reddit.com", "pinterest.com", "tumblr.com",
    "wordpress.com", "shopify.com", "squarespace.com", "wix.com",

    # Education
    "mit.edu", "stanford.edu", "harvard.edu", "berkeley.edu", "ox.ac.uk",
    "cam.ac.uk", "iitb.ac.in", "iitd.ac.in", "iisc.ac.in", "nus.edu.sg",
    "u-tokyo.ac.jp", "ethz.ch", "utoronto.ca", "unimelb.edu.au",

    # Government / institutional
    "usa.gov", "irs.gov", "cdc.gov", "nasa.gov", "fbi.gov", "nih.gov",
    "gov.uk", "canada.ca", "india.gov.in", "europa.eu", "un.org",
    "who.int", "worldbank.org", "imf.org", "rbi.org.in", "sec.gov",

    # Finance / banks
    "chase.com", "bankofamerica.com", "wellsfargo.com", "hsbc.com",
    "citibank.com", "paypal.com", "visa.com", "mastercard.com",
    "sbi.co.in", "hdfcbank.com", "icicibank.com", "americanexpress.com",
    "goldmansachs.com", "jpmorgan.com", "schwab.com", "fidelity.com",

    # E-commerce / retail
    "ebay.com", "walmart.com", "target.com", "bestbuy.com", "etsy.com",
    "alibaba.com", "flipkart.com", "myntra.com", "ikea.com", "costco.com",
    "homedepot.com", "zara.com", "nike.com", "adidas.com",

    # Media / news
    "bbc.com", "cnn.com", "nytimes.com", "reuters.com", "bloomberg.com",
    "theguardian.com", "forbes.com", "wsj.com", "npr.org", "aljazeera.com",
    "timesofindia.com", "hindustantimes.com", "thehindu.com",

    # Healthcare
    "mayoclinic.org", "webmd.com", "clevelandclinic.org", "nhs.uk",
    "hopkinsmedicine.org",

    # Airlines / travel
    "delta.com", "united.com", "airindia.in", "emirates.com",
    "booking.com", "expedia.com", "airbnb.com", "tripadvisor.com",

    # Telecom / infra
    "att.com", "verizon.com", "vodafone.com", "jio.com", "airtel.in",
    "cloudflare.com", "akamai.com", "digitalocean.com", "godaddy.com",

    # Misc widely-known brands
    "coca-cola.com", "pepsi.com", "unilever.com", "samsung.com", "sony.com",
    "toyota.com", "ford.com", "tesla.com", "boeing.com", "3m.com",
    "starbucks.com", "mcdonalds.com", "disney.com", "pixar.com",
    "nationalgeographic.com", "khanacademy.org", "coursera.org", "edx.org",
    "duolingo.com", "canva.com", "figma.com", "notion.so", "trello.com",
    "asana.com", "zendesk.com", "hubspot.com", "mailchimp.com",
    "wikimedia.org", "archive.org", "python.org", "djangoproject.com",
    "kernel.org", "apache.org", "w3.org", "ietf.org",
]


def build_augmented_rows() -> pd.DataFrame:
    """
    For each curated legitimate domain, generate a few plausible URL
    shapes (bare root, www + root, root + simple path) so the augmented
    set isn't itself a narrow artifact in the opposite direction.
    """
    rows = []
    for domain in LEGIT_DOMAINS:
        variants = [
            f"https://{domain}",
            f"https://www.{domain}",
            f"https://{domain}/",
        ]
        for url in variants:
            rows.append({"Labels": 0, "URLs": url, "source": "augmented_legit_domains"})
    return pd.DataFrame(rows)


def main():
    print("Loading original dataset (untouched, read-only)...")
    original = pd.read_excel(ORIGINAL_PATH)
    original["source"] = "original"
    print("Original shape:", original.shape)

    augmented = build_augmented_rows()
    print("Augmented rows generated:", augmented.shape)
    print("Unique augmented domains:", len(LEGIT_DOMAINS))

    combined = pd.concat([original, augmented], ignore_index=True)

    before = len(combined)
    combined = combined.drop_duplicates(subset=["URLs"])
    after = len(combined)
    if before != after:
        print(f"Dropped {before - after} duplicate URLs after merge.")

    print("\nFinal combined shape:", combined.shape)
    print("Final label distribution:")
    print(combined["Labels"].value_counts())
    print("\nBy source:")
    print(combined.groupby(["source", "Labels"]).size())

    combined.to_csv(OUTPUT_PATH, index=False)
    print(f"\nSaved combined dataset to: {OUTPUT_PATH}")
    print("Original Excel file was NOT modified.")


if __name__ == "__main__":
    main()