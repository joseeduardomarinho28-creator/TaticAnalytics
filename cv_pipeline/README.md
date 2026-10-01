# TaticAnalytics — CV Pipeline

Pipeline Python responsável por transformar vídeo de futebol em dados de
tracking consumidos pelo núcleo Java. Atualmente, o projeto valida vídeos e
seus metadados e oferece a calibração do campo de pixels para metros. Detecção,
tracking, classificação de times e exportação serão acrescentados nas próximas
issues.

## Requisitos

- Python **3.12 recomendado**. O código aceita 3.10–3.14, mas todos devem usar
  3.12 sempre que possível para reduzir diferenças entre máquinas.
- Aproximadamente 5 GB livres durante a instalação. PyTorch e seus componentes
  são grandes.
- Git e um terminal PowerShell, Bash ou Zsh.

Os vídeos, pesos de modelos e arquivos gerados são deliberadamente ignorados
pelo Git. Não tente versioná-los.

## Vídeo de referência do MVP

O material originalmente definido na TA-51 é o trecho de **2:30 a 3:00** de
[2026 World Cup: Argentina 3-0 Algeria — Full Match Tactical Cam](https://www.youtube.com/watch?v=t_nEnsBQ988).
Para respeitar as condições da fonte, obtenha-o por gravação de tela para uso
privado e acadêmico, em 1080p, sem marca d'água ou cronômetro. Não use sites ou
aplicativos de terceiros para baixar o vídeo e não versione o clipe no Git.

O clipe validado em 29 de setembro de 2026 usa aproximadamente **2:59 a 3:29**
do vídeo-fonte. O início foi adiantado porque a gravação ainda exibia os
controles do player e a janela imediatamente anterior acumulava um giro maior
da câmera. Essa escolha mantém o círculo central ou uma das grandes áreas
visível durante todo o trecho.

Recorte e normalize a gravação para 30 segundos exatos, ajustando o instante
inicial e o recorte das bordas à sua gravação. `-frames:v 900` garante a
quantidade exata mesmo quando a captura original usa taxa de quadros variável:

```bash
ffmpeg -ss <inicio> -i gravacao.mov \
  -vf "crop=<largura>:<altura>:<x>:<y>,scale=1920:1080:flags=lanczos,fps=30" \
  -frames:v 900 -an -c:v libx264 -crf 18 -pix_fmt yuv420p \
  -movflags +faststart trecho_mvp.mp4
```

Depois, execute a inspeção completa documentada abaixo e compare com o esperado:
aproximadamente 30 fps, 1920×1080 e 900 frames. Diferenças reais devem ser
registradas na TA-51, nunca corrigidas artificialmente no código.

### Resultado da validação da TA-51

O arquivo validado permanece fora do Git. Ele contém 900 quadros H.264,
1920×1080, 30 fps constantes e 30,000 s de duração. A decodificação integral
confirmou os 900 quadros, e a detecção automática de mudança de cena não
encontrou cortes ou replays.

A homografia estimada com ORB/RANSAC entre o primeiro quadro e quadros a cada
5 segundos mediu o movimento abaixo. Os `inliers` permaneceram entre 30% e 51%
dos matches, portanto todas as cinco estimativas são utilizáveis.

| Tempo | Deslocamento do centro | Escala |
| ---: | ---: | ---: |
| 5 s | (-94, -14) px | 1,046 |
| 10 s | (-217, -9) px | 1,108 |
| 15 s | (-235, +3) px | 1,122 |
| 20 s | (-256, -13) px | 1,068 |
| 25 s | (-354, +12) px | 1,100 |

O movimento é gradual, mas relevante: não reutilize uma única homografia nos
900 quadros. Calibre um quadro para a TA-58 e use a propagação/recalibração da
TA-109 antes de interpretar posições métricas ao longo do clipe inteiro.

## Instalação limpa

Abra o terminal na pasta `cv_pipeline` e confirme a versão do Python:

```bash
python --version
```

### macOS e Linux

```bash
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python scripts/check_environment.py
```

Se o comando `python3.12` não existir, instale o Python 3.12 antes de continuar.

### Windows PowerShell

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python scripts/check_environment.py
```

Se o PowerShell bloquear a ativação, execute uma vez na sessão atual:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Sempre que abrir um terminal novo, ative novamente a `.venv` antes de rodar o
pipeline. O diagnóstico deve terminar com `Environment is ready.` e informar se
o PyTorch usará CPU, CUDA ou Apple Metal.

### GPU NVIDIA

O `requirements.txt` instala a distribuição padrão do PyTorch. Caso seja
necessário usar CUDA, instale a variante indicada para o seu sistema pelo
seletor oficial do PyTorch e depois execute novamente o diagnóstico. Não altere
as versões dos outros pacotes individualmente sem atualizar e testar este
arquivo para toda a equipe.

`requirements.in` registra as cinco dependências diretas escolhidas pelo
projeto. `requirements.txt` é o congelamento completo e testado, incluindo
dependências transitivas. Para atualizar qualquer pacote, altere primeiro o
arquivo `.in`, recrie uma `.venv` limpa, gere novamente o congelamento com
`python -m pip freeze > requirements.txt` e execute todos os testes. Não atualize
o arquivo congelado parcialmente.

Quem for modificar o código deve instalar também as ferramentas de qualidade:

```bash
python -m pip install -r requirements-dev.txt
ruff check src scripts tests
ruff format --check src scripts tests
```

## Inspecionar um vídeo

Coloque o vídeo em qualquer pasta local e passe seu caminho explicitamente:

```bash
python src/main.py --video "/caminho/para/trecho_mvp.mp4"
```

No Windows:

```powershell
python src/main.py --video "C:\Videos\trecho_mvp.mp4"
```

Saída esperada:

```text
TaticAnalytics — video inspection
Video: /caminho/absoluto/trecho_mvp.mp4
FPS: 30.000
Resolution: 1920x1080
Reported frames: 900
Duration: 30.000 s
```

Alguns contêineres de vídeo informam uma contagem de frames incorreta. Para
decodificar o arquivo inteiro e comparar a contagem real, use:

```bash
python src/main.py --video "/caminho/para/trecho_mvp.mp4" --verify-frame-count
```

Essa verificação é mais lenta, mas deve ser feita ao receber um vídeo novo. Se
as contagens divergirem, o comando imprime um aviso e as etapas seguintes devem
usar a contagem decodificada.

## Calibração do campo (TA-58)

A calibração converte pixels da imagem para o sistema de coordenadas de
105 × 68 metros consumido pelo núcleo Java. A origem é o canto superior esquerdo
do campo visto pela câmera principal; x cresce para a direita e y para baixo.

Escolha de seis a oito referências visíveis e bem distribuídas pelo campo
(qualidade A sempre que possível) e execute:

```bash
python src/calibrate_click.py frame.jpg \
  --output output/calibration-keyframe-001.json \
  --frame-index 1
```

Sem `--points`, a ferramenta lista todas as referências disponíveis. Informe os
nomes escolhidos e clique em cada posição na ordem solicitada. Pressione `U`
para desfazer, `Esc` para cancelar ou `Enter` após capturar todos os pontos.

O JSON gerado registra o frame de origem, pontos clicados em pixels, posições
oficiais em metros, matriz de transformação, inliers do RANSAC e erro por ponto.
Uma imagem de sobreposição também é criada ao lado do JSON. Sempre a inspecione:
linhas laterais, meio-campo, áreas, círculo central e o marcador vermelho
`(0,0)` devem coincidir com o campo fotografado. Essa conferência detecta uma
calibração espelhada que testes baseados apenas em distância não detectam.

Refaça qualquer ponto cujo erro seja muito maior que os demais. O limite padrão
do RANSAC é 1 metro. Quatro pontos são o mínimo matemático, mas o fluxo normal
exige de seis a oito; `--allow-four` é reservado para frames sem referências
adicionais confiáveis.

Nas detecções, transforme o centro inferior da caixa (os pés do jogador), nunca
o centro da caixa:

```python
from calibration import bbox_foot_to_metre, load_calibration

calibration = load_calibration("output/calibration-keyframe-001.json")
x_m, y_m = bbox_foot_to_metre(calibration.matrix, x1, y1, x2, y2)
```

## Testes

Os testes criam vídeos e dados sintéticos temporários; nenhum vídeo do projeto
é necessário:

```bash
python -m unittest discover -s tests -v
```

Antes de abrir um PR, execute sempre:

```bash
python scripts/check_environment.py
python -m unittest discover -s tests -v
ruff check src scripts tests
ruff format --check src scripts tests
python src/main.py --video "/caminho/para/trecho_mvp.mp4" --verify-frame-count
git status --short
```

O último comando não deve listar `.venv`, vídeos, pesos (`.pt`) nem arquivos de
`output/`. Apenas `output/.gitkeep` permanece no repositório para preservar a
pasta vazia.

## Solução de problemas

- **`Video not found`**: confira o caminho e mantenha aspas quando houver
  espaços.
- **`OpenCV could not open the video`**: o arquivo pode estar incompleto ou usar
  um codec ausente. Tente reproduzi-lo em outro programa e obtenha novamente o
  clipe se necessário.
- **Import ausente**: ative a `.venv` correta e repita
  `python -m pip install -r requirements.txt`.
- **Ambiente aponta para outro Python**: remova apenas a pasta local `.venv`,
  recrie-a seguindo esta documentação e rode o diagnóstico.
- **Instalação do PyTorch falha**: confira sistema, arquitetura e Python no
  seletor oficial do PyTorch. Não use uma versão diferente silenciosamente;
  registre a incompatibilidade antes de alterar o arquivo de dependências.
