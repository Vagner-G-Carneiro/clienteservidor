# Como rodar (sem Maven, só com Java)

Esta pasta é **autossuficiente**: tem o programa já empacotado (`clienteservidor.jar`)
e os atalhos. Não precisa compilar nada nem instalar Maven na máquina.

## Único pré-requisito
Ter **Java 21 ou superior** instalado. Para conferir, abra um terminal e digite:

```
java -version
```

## Rodar com um clique

| Quero abrir o... | Windows (duplo-clique) | Linux/Mac |
|---|---|---|
| Servidor | `Servidor.bat` | `./Servidor.sh` |
| Cliente  | `Cliente.bat`  | `./Cliente.sh`  |
| Demonstração (1 servidor + 2 clientes) | `Demo.bat` | `./Demo.sh` |

> No Linux, na primeira vez pode ser preciso liberar a execução:
> `chmod +x *.sh`

### Pelo terminal (funciona em qualquer sistema)
```
java -jar clienteservidor.jar            # abre um menu: Servidor ou Cliente
java -jar clienteservidor.jar servidor   # abre direto o Servidor
java -jar clienteservidor.jar cliente    # abre direto o Cliente
```
Pode rodar o comando várias vezes ao mesmo tempo: 1 servidor + vários clientes.

## Ordem de uso
1. **Servidor:** abra → digite a **porta** (ex.: `21111`) → **Iniciar**.
2. **Cliente:** abra → preencha **IP** e **Porta** do servidor → **Conectar**
   → **Cadastrar** e depois **Login**.
   - Mesma máquina: IP = `127.0.0.1`
   - Outra máquina na rede: IP da máquina do servidor (descubra com `ipconfig`
     no Windows ou `ip a` no Linux).
3. **Conversar:** dê duplo-clique num usuário da lista (ou marque **"Para todos
   (/todos)"** para broadcast), escreva a mensagem e clique **Enviar**.

## Entre máquinas diferentes
- Servidor e clientes precisam estar na **mesma rede**.
- O **firewall** da máquina do servidor não pode bloquear a **porta** escolhida.
