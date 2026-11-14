# Manual de uso do NotasWorkflow para Android

Versão 1.12.2, com as mesmas funções do complemento Notas Workflow NVDA 1.12.0.

O NotasWorkflow organiza folhas (anotações e tarefas) em quatro raias de status: **A fazer**, **Em andamento**, **Bloqueada** e **Concluída**. As folhas podem ficar soltas, direto numa pasta ou num caderno, e um caderno pode estar dentro de uma pasta. Tudo funciona com o TalkBack, com o toque e com um teclado físico.

## As abas

Na parte de baixo da tela ficam quatro abas. O TalkBack fala cada uma como guia, com a posição e se está selecionada:

- **Folhas**: o quadro com as raias (a tela principal).
- **Buscar**: a busca nas folhas.
- **Cadernos e pastas**: organizar cadernos e pastas.
- **Mais**: Exportar, Quantas folhas em cada raia, Configurações e Manual de uso.

Em qualquer aba, o botão Voltar leva para a aba Folhas.

## A tela principal

A aba **Folhas** tem, de cima para baixo:

- O botão **Nova folha**, que abre a janela Nova folha na hora.
- O botão **Mostrar**, que diz o que está sendo mostrado e quantas folhas há. Ele abre uma lista com Todas as folhas, Folhas soltas, Folhas recentes (as 10 alteradas por último), cada pasta com os cadernos dela dentro e os cadernos sem pasta. As pastas começam fechadas: o botão ao lado de cada pasta abre e fecha. Escolher um item mostra as folhas dele.
- As quatro raias, como títulos ("A fazer, 3 folhas"). Com o TalkBack, deslize com dois dedos ou use a navegação por títulos para ir de uma raia a outra. As raias começam fechadas: toque duas vezes para abrir ou fechar. Com teclado, Seta para a direita abre e Seta para a esquerda fecha.
- O botão **Criar…**, no canto de baixo.

Cada raia mostra 200 folhas por vez; **Mostrar mais folhas**, no fim da raia, mostra as próximas. Uma raia aberta termina com **Adicionar uma folha**, que cria uma folha já nessa raia.

A tela tem o visual de um quadro do Trello: o fundo azul é o quadro, cada raia é uma lista cinza e cada folha é um cartão branco com uma etiqueta na cor da raia (azul para A fazer, laranja para Em andamento, vermelho para Bloqueada e verde para Concluída). No tema escuro do celular, as cores ficam escuras.

### Uma folha na lista

O TalkBack fala o título, o lugar (caderno ou pasta) e a data da última alteração. Tocar duas vezes abre a folha. As outras ações ficam no menu de ações do TalkBack (deslize para cima e depois para a direita, ou toque com três dedos, conforme a configuração do seu TalkBack) e também num toque longo:

- **Abrir**
- **Próximo status** e **Status anterior**: mudam a raia da folha. O TalkBack fala "Status atualizado para Em andamento", e o foco vai para a próxima folha da raia em que ela estava.
- **Subir na raia** e **Descer na raia**: mudam a ordem dentro da raia. O TalkBack fala a nova posição ("Posição 2 de 5"). Em Folhas recentes a ordem é a da última alteração e não pode ser mudada.
- **Mover para…**: escolhe o local (solta, uma pasta ou um caderno).
- **Exportar…**: salva a folha como documento.
- **Excluir**: pergunta antes ("Excluir a folha Compras? Isso não pode ser desfeito.").

Com teclado físico: **Alt+Shift+Seta para a direita** e **Alt+Shift+Seta para a esquerda** mudam o status, **Ctrl+Shift+Seta para cima** e **Ctrl+Shift+Seta para baixo** sobem e descem a folha na raia, **Delete** exclui e **Enter** abre.

### A aba Mais

- **Exportar…**: exporta um caderno ou uma pasta.
- **Quantas folhas em cada raia**: o TalkBack fala o número de folhas de cada raia do que está sendo mostrado.
- **Configurações…**
- **Manual de uso…**: este manual.

## Criar

O botão **Nova folha**, no topo da tela principal, e **Adicionar uma folha**, no fim de cada raia aberta, abrem direto a janela Nova folha (a segunda já com o status da raia). O botão **Criar…** abre um menu com:

- **Folha…**: pede o **Título**, o **Local** e o **Status**. O local já vem com o caderno ou a pasta que está sendo mostrada. Ao lado do local ficam **Novo caderno…** e **Nova pasta…**: o que você cria ali já fica escolhido como local. **Criar** cria a folha e abre a janela dela, no conteúdo.
- **Caderno…**: pede o **Nome** e a **Pasta** (Nenhuma pasta ou uma das pastas), com **Nova pasta…**.
- **Pasta…**: pede o nome da nova pasta.
- **Folha a partir de documento…**: veja abaixo.

## A janela da folha

A janela da folha tem:

- **Título**.
- **Local** e **Status**: botões que abrem a escolha do lugar e do status.
- **Formatação…** e **Inserir documento…**.
- **Situação do salvamento**: "Salva", "Alterações não salvas…" ou "Salva às 14:30".
- **Conteúdo**: o texto da folha.
- Na barra do topo, **Salvar e fechar** (o botão Voltar faz o mesmo) e **Salvar**.

A folha salva sozinha 2 segundos depois que você para de digitar (o tempo muda nas Configurações). Salvar salva na hora e fechar sempre salva; sair do aplicativo também salva. Nada se perde.

Se você mudar o status ou o local da folha, ao fechar a janela o foco vai para a próxima folha da raia em que ela estava, para tratar uma raia uma folha depois da outra.

### A marcação do conteúdo

O conteúdo é texto com uma marcação leve, a mesma do complemento do NVDA:

- `## Título` ou `### Título`: título 2 ou 3.
- `- item`: lista com marcadores.
- `1. item`: lista numerada.
- `> texto`: citação.
- `---`: divisor.
- `**texto**`: negrito (Ctrl+B no teclado físico).
- `*texto*`: itálico (Ctrl+I).
- `~~texto~~`: tachado.
- texto entre crases (o acento grave): código.

**Formatação…** abre a lista de todos os formatos: Negrito, Itálico, Tachado, Código, Título 2, Título 3, Lista com marcadores, Lista numerada, Citação e Divisor. O formato vale para o texto selecionado ou para a linha do cursor, e escolher de novo remove o formato ("Negrito removido"). Ctrl+S salva.

## Busca

A aba **Buscar** procura no título e no conteúdo enquanto você digita, sem diferenciar acentos e maiúsculas: "acao" encontra "Ação". O TalkBack fala quantas folhas foram encontradas. Cada resultado tem o título, um trecho do texto, a relevância e a data; tocar duas vezes abre a folha.

## Cadernos e pastas

A aba **Cadernos e pastas** mostra cada pasta com os seus cadernos e as suas folhas, um ramo **Sem pasta** com os outros cadernos e um ramo **Folhas soltas**. Os ramos começam fechados: toque duas vezes para abrir ou fechar. Tocar duas vezes numa folha abre a folha.

As ações de cada item ficam no menu de ações do TalkBack e no toque longo:

- Pasta: **Criar folha aqui…**, **Criar caderno aqui…**, **Renomear…**, **Exportar…** e **Excluir**.
- Caderno: **Criar folha aqui…**, **Renomear…**, **Mover para…** (outra pasta ou Sem pasta), **Exportar…** e **Excluir**.
- Folha: **Abrir**, **Renomear…** (muda o título), **Mover para…**, **Exportar…** e **Excluir**.

Com teclado físico, **F2** renomeia e **Delete** exclui. O botão **Criar…** no topo cria uma folha, um caderno ou uma pasta.

Excluir um caderno exclui também as folhas dele. Excluir uma pasta exclui os cadernos dela, as folhas desses cadernos e as folhas guardadas direto nela. A pergunta diz quanto será excluído: "Excluir a pasta Projetos e tudo o que está nela: 2 cadernos e 5 folhas? Isso não pode ser desfeito."

## Folha a partir de documento

**Criar… > Folha a partir de documento…** pede um arquivo do Word (.docx), do PowerPoint (.pptx), de texto (.txt), uma página HTML ou um e-mail (.eml). O documento é lido em segundo plano e a janela Nova folha já vem com o título dele (ou o assunto do e-mail); a folha nasce com o texto no conteúdo.

Títulos, listas, negrito, itálico e tachado continuam. Cada slide vira um título, com as anotações do orador; cada linha de tabela vira uma linha ("a | b"); um e-mail começa com De, Para, Cc, Data e Anexos. Imagens não vêm.

Na janela da folha, **Inserir documento…** coloca o texto de um documento no cursor, em linhas próprias.

Uma folha guarda até 1 MB: documentos maiores são cortados, com aviso. Arquivos até 50 MB; documentos danificados, protegidos por senha, sem texto ou de outros tipos (.doc, .ppt, .rtf, .pdf, .msg) são avisados e nada é criado.

## Exportar como documento

**Exportar…** (no menu de uma folha, de um caderno ou de uma pasta, ou na aba Mais) pergunta o formato: **Documento do Word (.docx)**, **Markdown (.md)** ou **Página HTML (.html)**. Um caderno ou uma pasta pode ir para **Um arquivo com todas as folhas** ou para **Um arquivo para cada folha**: neste caso você escolhe uma pasta e é criada dentro dela uma pasta nova com o nome do caderno ou da pasta, com uma subpasta para cada caderno.

Cada folha leva o título, o status, o local, as datas e o conteúdo com a formatação; os títulos são títulos de verdade, fáceis de navegar com um leitor de telas. Nada é sobrescrito: um nome repetido ganha "(2)". O TalkBack fala quantas folhas foram exportadas e onde.

## Configurações

- **Salvar automaticamente depois de (segundos)**: de 1 a 30 segundos, 2 por padrão.
- **Anunciar o salvamento automático**: o TalkBack fala "Folha salva" a cada salvamento automático.
- **Arquivo das folhas**: por padrão as folhas ficam no armazenamento do aparelho. **Abrir arquivo de folhas…** usa um arquivo que já existe e **Criar arquivo de folhas…** cria um novo com as folhas atuais. **Usar o armazenamento do aparelho** volta ao padrão.
- **Importar folhas…** e **Exportar folhas…**: um arquivo .json com todas as folhas, cadernos e pastas.

### As mesmas folhas no celular e no computador

O complemento do NVDA pode guardar as folhas numa pasta do Google Drive ou do OneDrive (NVDA, Preferências, Configurações, Notas Workflow NVDA, Pasta das folhas). No celular, em Configurações, toque em **Abrir arquivo de folhas…** e escolha o arquivo **NotasWorkflowNVDA-data.json** dessa pasta. A partir daí o aplicativo lê e grava esse arquivo, e quando você volta ao aplicativo ele lê de novo o que mudou no computador.

O formato dos arquivos é o mesmo do complemento e do aplicativo web: Importar folhas aceita os arquivos exportados por qualquer um deles. Uma folha que já existe fica com a versão alterada por último.

## Segurança das folhas

Toda mudança é salva em segundo plano, de forma segura: o arquivo anterior fica guardado como cópia de segurança. Se o arquivo estiver danificado, ele é guardado com outro nome e a cópia de segurança é usada; o aplicativo avisa.

O aplicativo não usa a internet: nenhuma folha sai do aparelho, a não ser para os arquivos que você escolher.

## Histórico deste manual

- 1.12.2: abas na parte de baixo (Folhas, Buscar, Cadernos e pastas, Mais) no lugar das opções do topo.
- 1.12.1: visual do Trello (cores, tipografia, cartões e ícones de linha); botão Nova folha na tela principal e Adicionar uma folha no fim de cada raia.
- 1.12.0: primeira versão para Android, com as funções do complemento Notas Workflow NVDA 1.12.0.
