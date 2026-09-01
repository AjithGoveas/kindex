# Builds static native libraries (SQLite with FTS5, tree-sitter) for Kotlin/Native targets.
# Windows host: produces MinGW-w64 COFF archives under third_party/dist/mingwX64/.
# Uses Kotlin/Native's bundled LLVM clang plus its msys2-mingw-w64 sysroot (~/.konan/dependencies).

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$thirdParty = Join-Path $repoRoot "third_party"

function Find-KonanToolchain {
    $konan = Join-Path $env:USERPROFILE ".konan\dependencies"
    if (-not (Test-Path $konan)) { throw "Kotlin/Native dependencies not found at $konan" }

    $clang = Get-ChildItem $konan -Directory -Filter "llvm-*-windows-essentials-*" |
        ForEach-Object { Join-Path $_.FullName "bin\clang.exe" } |
        Where-Object { Test-Path $_ } |
        Sort-Object { [int]([regex]::Match($_, 'llvm-(\d+)').Groups[1].Value) } |
        Select-Object -Last 1
    if (-not $clang) { throw "clang.exe not found under $konan (llvm-*-windows-essentials-*)" }

    $sysroot = Get-ChildItem $konan -Directory -Filter "msys2-mingw-w64-*" | Select-Object -First 1
    if (-not $sysroot) { throw "msys2-mingw-w64 sysroot not found under $konan" }
    if (-not (Test-Path (Join-Path $sysroot.FullName "x86_64-w64-mingw32\include\stdio.h"))) {
        throw "mingw sysroot incomplete at $($sysroot.FullName)"
    }

    $ar = Get-ChildItem (Join-Path $sysroot.FullName "bin") -Filter "ar.exe" | Select-Object -First 1
    if (-not $ar) { throw "ar.exe not found in $($sysroot.FullName)\bin" }

    return @{
        Clang   = $clang
        Sysroot = $sysroot.FullName
        Ar      = $ar.FullName
    }
}

function Build-Archive {
    param(
        [hashtable]$Toolchain,
        [string]$OutDir,
        [string]$ArchiveName,
        [string[]]$Sources,
        [string[]]$Defines,
        [string[]]$IncludeDirs,
        [string]$WorkName
    )

    $work = Join-Path $env:TEMP "kindex-nativelibs-$WorkName"
    if (Test-Path $work) { Remove-Item $work -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $work | Out-Null
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

    $objects = @()
    $index = 0
    foreach ($src in $Sources) {
        $obj = Join-Path $work ("{0}_{1}.o" -f $WorkName, $index)
        $index++
        $args = @(
            "--target=x86_64-w64-mingw32",
            "--sysroot=$($Toolchain.Sysroot)",
            "-c", $src, "-o", $obj,
            "-O2", "-fPIC", "-DNDEBUG"
        ) + ($Defines | ForEach-Object { "-D$_" })
        foreach ($inc in $IncludeDirs) { $args += @("-I", $inc) }
        & $Toolchain.Clang @args
        if ($LASTEXITCODE -ne 0) { throw "clang failed for $src" }
        $objects += $obj
    }

    $archive = Join-Path $OutDir $ArchiveName
    if (Test-Path $archive) { Remove-Item $archive -Force }
    & $Toolchain.Ar rcs $archive @objects
    if ($LASTEXITCODE -ne 0) { throw "ar failed for $ArchiveName" }
    return $archive
}

$toolchain = Find-KonanToolchain
Write-Host "Using clang:   $($toolchain.Clang)"
Write-Host "Using sysroot: $($toolchain.Sysroot)"

$outDir = Join-Path $thirdParty "dist\mingwX64"

$sqliteArchive = Build-Archive `
    -Toolchain $toolchain `
    -OutDir $outDir `
    -ArchiveName "libsqlite3.a" `
    -Sources @((Join-Path $thirdParty "sqlite\sqlite3.c")) `
    -Defines @("SQLITE_ENABLE_FTS5", "SQLITE_OMIT_LOAD_EXTENSION", "SQLITE_THREADSAFE=1") `
    -IncludeDirs @((Join-Path $thirdParty "sqlite")) `
    -WorkName "sqlite"

$treesitterSources = @(Join-Path $thirdParty "tree-sitter\lib.c")
Get-ChildItem (Join-Path $thirdParty "grammars") -Directory | ForEach-Object {
    $treesitterSources += (Join-Path $_.FullName "parser.c")
    $scanner = Join-Path $_.FullName "scanner.c"
    if (Test-Path $scanner) { $treesitterSources += $scanner }
}

$treesitterArchive = Build-Archive `
    -Toolchain $toolchain `
    -OutDir $outDir `
    -ArchiveName "libtreesitter.a" `
    -Sources $treesitterSources `
    -Defines @() `
    -IncludeDirs @((Join-Path $thirdParty "tree-sitter\include"), (Join-Path $thirdParty "tree-sitter")) `
    -WorkName "treesitter"

Write-Host ""
Write-Host "Static libraries built:"
Write-Host "  $sqliteArchive ($([math]::Round((Get-Item $sqliteArchive).Length/1MB,1)) MB)"
Write-Host "  $treesitterArchive ($([math]::Round((Get-Item $treesitterArchive).Length/1MB,1)) MB)"
