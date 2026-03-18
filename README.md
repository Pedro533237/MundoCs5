# MundoCs5

Mod Fabric (1.21.9) com geração radial inspirada no mapa da imagem:

- 🍄 Centro: `mushroom_fields`
- 🍕 Anel interno: biomas em fatias por ângulo
- 🌊 Anel externo: oceanos por ângulo (warm → temperate → cold)
- 🌎 Fora do raio: fallback para gerador vanilla (`MultiNoiseBiomeSource`)

## Lógica principal

Implementada em `PizzaBiomeSource#getBiome`:

- calcula a distância radial e aplica warps orgânicos no raio;
- usa o centro como `mushroom_fields` até `center_radius`;
- escolhe a fatia principal/secundária por ângulo dentro do anel de terra;
- faz transição praia/terra/oceano no anel externo;
- volta para o `vanillaBiomeSource` depois de `outer_world_start`.

## Registro do biome source

O tipo custom `mundocs5:pizza` **já precisa estar registrado no jogo** para o preset funcionar. Neste projeto isso acontece em:

- `src/main/java/dev/mundocs5/ModWorldgen.java`
- chamado por `src/main/java/dev/mundocs5/MundoCs5Mod.java`

Ao inicializar o mod, o código registra explicitamente `PizzaBiomeSource.CODEC` em `Registries.BIOME_SOURCE` e escreve um log confirmando o registro.

## Arquivos principais

- `src/main/java/dev/mundocs5/world/PizzaBiomeSource.java`
- `src/main/java/dev/mundocs5/ModWorldgen.java`
- `src/main/java/dev/mundocs5/MundoCs5Mod.java`
- `src/main/resources/data/mundocs5/worldgen/world_preset/pizza_world.json`
- `src/main/resources/data/mundocs5/worldgen/dimension/pizza_overworld.json`

## Como usar

1. Build do mod com Gradle/Loom.
2. Instale no cliente/servidor Fabric 1.21.9 (Java 21).
3. Crie um novo mundo usando o preset `mundocs5:pizza_world`.
4. Confirme no log que `mundocs5:pizza` foi registrado antes de criar o mundo.

## Ajustes rápidos

Você pode mudar no JSON:

- `layout_seed`
- `center_radius`
- listas de `*_biomes`
- `ocean_start`
- `outer_world_start`
- biomas de praia e oceano

Se o preset for ignorado em runtime, os primeiros pontos para conferir são:

1. o log de inicialização mostrando o registro de `mundocs5:pizza`;
2. se o mundo foi criado com o preset `mundocs5:pizza_world`;
3. se o JSON do biome source continua compatível com os campos definidos no `MapCodec`.
