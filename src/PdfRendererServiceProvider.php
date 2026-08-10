<?php

namespace Blutrixx\PdfRenderer;

use Illuminate\Support\ServiceProvider;

class PdfRendererServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(PdfRenderer::class);
    }
}
