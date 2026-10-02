# Compartilhamento familiar

As regras destinam-se ao Firestore com Security Rules e ao contrato Android deste repositório. Nenhum projeto, banco remoto ou recurso faturável é provisionado por estes arquivos. O teste usa exclusivamente `demo-amamenta-bebe` no emulador local.

Execute em `backend`: `npm ci`, depois `npm run test:emulator` (Node e Java necessários). O comando carrega as regras de `firebase.json` e executa os testes contra o emulador. Publique regras somente no projeto Firebase real configurado pelo responsável; não utilize regras de desenvolvimento abertas.

## Auditoria do modelo

- Cada leitura e escrita de registros exige participação na família correspondente. Remover um cuidador revoga novas operações no servidor; dados offline já baixados não podem ser apagados remotamente com garantia.
- A criação da família e do administrador ocorre num lote atômico. O proprietário não pode ser alterado e o administrador não pode ser removido ou promovido por um cuidador.
- Convites utilizam documentos identificados pelo SHA-256 de um token aleatório gerado pelo cliente. A regra exige destinatário com e-mail verificado, validade máxima de 24 horas e aceitação atômica com criação da participação. Não existe consulta pública de convites.
- Registros usam revisões consecutivas, autor imutável, carimbo de tempo do servidor e mapas tipados com limites para horários, duração, quantidade e observações. Campos ausentes, desconhecidos ou de tipo incorreto são rejeitados no servidor. Exclusões usam tombstones; exclusão física é proibida para preservar sincronização.
- Room conserva a representação JSON interna. O cliente converte os mapas remotos e valida novamente antes de importar para Room; não aceita JSON opaco no servidor.

Os testes cobrem acesso anônimo e entre famílias, revogação de participante, elevação de privilégio, adulteração de autor, revisão inválida, convite expirado/reutilizado/revogado, aceitação sem lote completo, e-mail incorreto/não verificado e excesso de tamanho. Eles não substituem testes Android em dois celulares com autenticação e rede reais. A configuração externa precisa do projeto Firebase, Authentication por e-mail/senha, Firestore e publicação destas regras antes de declarar a sincronização pronta para uso.
