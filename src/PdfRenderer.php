<?php

namespace Blutrixx\PdfRenderer;

class PdfRenderer
{
    public function renderPage(string $path, int $page, int $width = 800, ?string $outputPath = null): array
    {
        $params = ['path' => $path, 'page' => $page, 'width' => $width];
        if ($outputPath !== null) {
            $params['output_path'] = $outputPath;
        }

        $raw = nativephp_call('PdfRenderer.RenderPage', json_encode($params));

        return is_array($raw) ? $raw : (json_decode($raw ?? '{}', true) ?? []);
    }

    public function getPageCount(string $path): int
    {
        $raw    = nativephp_call('PdfRenderer.GetPageCount', json_encode(['path' => $path]));
        $result = is_array($raw) ? $raw : (json_decode($raw ?? '{}', true) ?? []);

        return (int) ($result['pageCount'] ?? 0);
    }

    public function getPageDimensions(string $path, int $page): array
    {
        $raw    = nativephp_call('PdfRenderer.GetPageDimensions', json_encode(['path' => $path, 'page' => $page]));
        $result = is_array($raw) ? $raw : (json_decode($raw ?? '{}', true) ?? []);

        return ['width' => (int) ($result['width'] ?? 0), 'height' => (int) ($result['height'] ?? 0)];
    }

    public function renderThumbnail(string $path, string $outputPath, int $width = 320): array
    {
        $raw = nativephp_call('PdfRenderer.RenderThumbnail', json_encode([
            'path'        => $path,
            'output_path' => $outputPath,
            'width'       => $width,
        ]));

        return is_array($raw) ? $raw : (json_decode($raw ?? '{}', true) ?? []);
    }
}
