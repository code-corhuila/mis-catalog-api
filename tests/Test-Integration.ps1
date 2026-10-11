param([Parameter(Mandatory)][string]$BaseUrl,[Parameter(Mandatory)][string]$SecurityUrl,[Parameter(Mandatory)][string]$SecurityEnv)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7) { throw 'Se requiere PowerShell 7.' }
$settings=@{}
Get-Content -LiteralPath $SecurityEnv | ForEach-Object { if($_ -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]]=$matches[2] } }
function Check($condition,$message) { if(-not $condition) { throw $message } }
function Send($method,$path,$body,$token,$extra,$expected) {
    $trace=[guid]::NewGuid().ToString(); $headers=@{'X-Correlation-Id'=$trace;'X-User-Role'='ADMIN'}
    if($token) { $headers.Authorization='Bearer '+$token }
    foreach($key in $extra.Keys) { $headers[$key]=$extra[$key] }
    $options=@{Uri=$BaseUrl.TrimEnd('/')+$path;Method=$method;Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if($null -ne $body) { $options.Body=($body | ConvertTo-Json -Compress); $options.ContentType='application/json' }
    $response=Invoke-WebRequest @options
    Check ($response.StatusCode -eq $expected) "$method ${path}: HTTP $($response.StatusCode), esperado $expected"
    $data=$response.Content | ConvertFrom-Json
    Check ($response.Headers['X-Correlation-Id'][0] -eq $trace) 'Correlacion perdida.'
    if($expected -ge 400) { Check ($data.traceId -eq $trace -and $data.error -and $data.message) 'Error fuera de contrato.' }
    return @{Data=$data;Headers=$response.Headers}
}
[void](Send GET '/health' $null $null @{} 200)
[void](Send GET '/api/v1/medications' $null $null @{} 401)
[void](Send GET '/api/v1/medications' $null 'invalid' @{} 401)
$tokens=@{}
foreach($entry in @(@('admin','ADMIN'),@('inventario','INVENTORY'),@('supervisor','SUPERVISOR'),@('consulta','CONSULTATION'))) {
    $password=$settings['BOOTSTRAP_'+$entry[1]+'_PASSWORD']; Check (-not [string]::IsNullOrEmpty($password)) 'Falta configuracion bootstrap.'
    $login=Invoke-RestMethod -Uri ($SecurityUrl.TrimEnd('/')+'/api/v1/auth/login') -Method Post -ContentType 'application/json' -Body (@{username=$entry[0];password=$password} | ConvertTo-Json -Compress)
    $tokens[$entry[1]]=$login.accessToken
}
$suffix=[guid]::NewGuid().ToString('N').Substring(0,12).ToUpperInvariant()
$body=@{code="OFF-$suffix";name='Medicamento sintetico oficial';activeIngredient='Sintetico';pharmaceuticalForm='Tableta';concentration='10 mg';unitOfMeasure='Unidad'}
$key="official-$suffix"; $intent=@{'Idempotency-Key'=$key}
$created=Send POST '/api/v1/medications' $body $tokens.ADMIN $intent 201
$id=$created.Data.id; $path="/api/v1/medications/$id"
Check ($created.Headers.Location[0] -eq $path) 'Location inesperado.'
Check (@($created.Data.PSObject.Properties).Count -eq 9) 'Recurso fuera de contrato.'
$replay=Send POST '/api/v1/medications' $body $tokens.ADMIN $intent 200
Check ($replay.Data.id -eq $id) 'Replay duplicado.'
$other=$body.Clone(); $other.name='Otra solicitud'
[void](Send POST '/api/v1/medications' $other $tokens.ADMIN $intent 409)
[void](Send POST '/api/v1/medications' $body $tokens.ADMIN @{'Idempotency-Key'="duplicate-$suffix"} 422)
foreach($role in @('ADMIN','INVENTORY','SUPERVISOR','CONSULTATION')) {
    [void](Send GET '/api/v1/medications?page=1&limit=20' $null $tokens[$role] @{} 200)
    [void](Send GET $path $null $tokens[$role] @{} 200)
    if($role -ne 'ADMIN') {
        [void](Send POST '/api/v1/medications' $body $tokens[$role] $intent 403)
        [void](Send PUT $path $body $tokens[$role] @{'If-Match'='"0"'} 403)
        [void](Send POST "$path/deactivate" @{} $tokens[$role] @{'If-Match'='"0"'} 403)
    }
}
[void](Send PUT $path $body $tokens.ADMIN @{} 428)
[void](Send PUT $path $body $tokens.ADMIN @{'If-Match'='W/"0"'} 400)
$invalid=$body.Clone(); $invalid.code=7
[void](Send POST '/api/v1/medications' $invalid $tokens.ADMIN @{'Idempotency-Key'="invalid-$suffix"} 400)
$invalid=$body.Clone(); $invalid.unexpected=$true
[void](Send POST '/api/v1/medications' $invalid $tokens.ADMIN @{'Idempotency-Key'="unknown-$suffix"} 400)
$invalid=$body.Clone(); $invalid.unitOfMeasure='Caja'
[void](Send PUT $path $invalid $tokens.ADMIN @{'If-Match'='"0"'} 422)
$updated=Send PUT $path $other $tokens.ADMIN @{'If-Match'='"0"'} 200
Check ($updated.Headers.ETag[0] -eq '"1"' -and $updated.Data.createdAt -eq $created.Data.createdAt) 'Version/creacion incorrectas.'
[void](Send PUT $path $other $tokens.ADMIN @{'If-Match'='"0"'} 412)
$noop=Send PUT $path $other $tokens.ADMIN @{'If-Match'='"1"'} 200
Check ($noop.Headers.ETag[0] -eq '"1"') 'No-op altero version.'
$inactive=Send POST "$path/deactivate" @{} $tokens.ADMIN @{'If-Match'='"1"'} 200
Check (-not $inactive.Data.active -and $inactive.Headers.ETag[0] -eq '"2"') 'Desactivacion incorrecta.'
$replay=Send POST '/api/v1/medications' $body $tokens.ADMIN $intent 200
Check ($replay.Data.active -and $replay.Data.name -eq $created.Data.name) 'Mantenimiento altero snapshot original.'
$read=Send GET $path $null $tokens.ADMIN @{} 200
Check (-not $read.Data.active -and $read.Data.name -eq $other.name) 'Estado no persistido.'
Write-Output 'PASS: JWT/roles reales, idempotencia, JSON estricto, ETag, mantenimiento y snapshot original.'
Write-Output "Fixture sintetico conservado en Catalogo: $id"
