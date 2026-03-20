# MundoCs5

Mod Fabric para **Minecraft 1.21.9** que gera um mundo inspirado na imagem de referência com formato mais irregular, menos circular e com aparência mais “pintada à mão”.

## O que o mod faz agora

- O `PizzaChunkGenerator` usa massas de terreno e cortes manuais embutidos no código, em vez de depender só de um círculo deformado.
- Isso deixa a ilha com silhueta mais quebrada, com entradas de água, bordas irregulares, lagoa central e regiões mais parecidas com o mapa mostrado.
- O relevo continua misturando esse desenho manual com o comportamento vanilla do Minecraft para ficar mais natural ao jogar.
- O preset principal continua sendo `mundocs5:pizza_world`, pronto para instalar e usar.

## Arquivos importantes

- `src/main/java/dev/mundocs5/world/PizzaBiomeSource.java`
- `src/main/java/dev/mundocs5/world/PizzaChunkGenerator.java`
- `src/main/java/dev/mundocs5/world/OrganicNoise.java`
- `src/main/resources/data/mundocs5/worldgen/dimension/pizza_overworld.json`
- `src/main/resources/data/mundocs5/worldgen/world_preset/pizza_world.json`

## Como usar

1. Compile o mod com Gradle.
2. Coloque o `.jar` gerado na pasta `mods` do Fabric.
3. Abra o Minecraft com Fabric.
4. Crie um novo mundo usando o preset `mundocs5:pizza_world`.
5. Entre no mundo para jogar no mapa gerado pelo mod.
