# TRACE – Refactored Frontend

This project is split by language/responsibility while preserving the existing UI and JavaScript behavior.

## Structure

- `index.html` — HTML structure and external library references
- `css/styles.css` — all page styles previously embedded in `<style>`
- `js/app.js` — all application JavaScript previously embedded in `<script>`
- `assets/` — reserved for local images/icons/fonts if added later

## Run

Open `index.html` from the project root or serve the directory with a static web server.

The existing external Font Awesome and Leaflet CDN references are intentionally preserved.
