# Blutrixx PDF Renderer

A [NativePHP Mobile](https://nativephp.com) plugin that renders PDF pages to images on-device, using Android's native `PdfRenderer` — no server round-trip, no bundled PDF.js. Useful for document previews, thumbnails, and page-by-page viewers.

Composer package: `blutrixx/nativephp-pdf-renderer`
Repo: `joelnjoshkibona/nativephp-pdf-renderer`
Current release: `v1.0.0`

## Requirements

- PHP ^8.1 (see the package's `composer.json` for the exact constraint)
- A Laravel app running under `nativephp/mobile`
- No extra Android permissions or dependencies — `android.graphics.pdf.PdfRenderer` is part of the Android SDK itself (API 21+).

## Installation

Not on Packagist yet.

**As a git submodule (how this repo itself consumes it):**

```bash
git submodule add https://github.com/joelnjoshkibona/nativephp-pdf-renderer.git packages/nativephp-pdf-renderer
```

```json
// composer.json
{
    "repositories": [
        {"type": "path", "url": "packages/nativephp-pdf-renderer"}
    ],
    "require": {
        "blutrixx/nativephp-pdf-renderer": "@dev"
    }
}
```

**Without a submodule:**

```json
{
    "repositories": [
        {"type": "vcs", "url": "https://github.com/joelnjoshkibona/nativephp-pdf-renderer"}
    ],
    "require": {
        "blutrixx/nativephp-pdf-renderer": "^1.0"
    }
}
```

Laravel auto-discovers `PdfRendererServiceProvider`.

## How the bridge works

Every method is **synchronous** — no events, no `native-event` listening. Call it, get a real result back in the same request. `$path` throughout is a path to a PDF file already on-device (e.g. one you downloaded and stored locally); this package doesn't fetch remote PDFs for you.

```php
use Blutrixx\PdfRenderer\Facades\PdfRenderer;

$pageCount = PdfRenderer::getPageCount($path);
```

## API reference

| Method | Returns | Notes |
|---|---|---|
| `renderPage(string $path, int $page, int $width = 800, ?string $outputPath = null)` | `array` | Renders `$page` (0-indexed) at `$width`px wide, preserving aspect ratio. If `$outputPath` is omitted the native side picks a location and returns it. |
| `getPageCount(string $path)` | `int` | |
| `getPageDimensions(string $path, int $page)` | `{width: int, height: int}` | Native PDF point dimensions for the given page |
| `renderThumbnail(string $path, string $outputPath, int $width = 320)` | `array` | Convenience wrapper for a smaller preview image, written to `$outputPath` |

```php
use Blutrixx\PdfRenderer\Facades\PdfRenderer;

$count = PdfRenderer::getPageCount('/storage/documents/invoice.pdf');

$dims = PdfRenderer::getPageDimensions('/storage/documents/invoice.pdf', page: 0);

$page = PdfRenderer::renderPage(
    path: '/storage/documents/invoice.pdf',
    page: 0,
    width: 1000,
);
// $page['path'] (or your own $outputPath) now points at a rendered PNG/JPEG on-device

PdfRenderer::renderThumbnail(
    path: '/storage/documents/invoice.pdf',
    outputPath: '/storage/thumbnails/invoice.png',
    width: 320,
);
```

## Quick start: a page viewer route

```php
// routes/api.php
Route::get('/documents/{document}/pages/{page}', function (Document $document, int $page) {
    return \Blutrixx\PdfRenderer\Facades\PdfRenderer::renderPage($document->local_path, $page, width: 1200);
});

Route::get('/documents/{document}/page-count', function (Document $document) {
    return ['pageCount' => \Blutrixx\PdfRenderer\Facades\PdfRenderer::getPageCount($document->local_path)];
});
```
