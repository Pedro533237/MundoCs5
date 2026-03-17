# MundoCs5

Mod Fabric (1.21.9) com geração radial inspirada no mapa da imagem:

- 🍄 Centro: `mushroom_fields`
- 🍕 Anel interno: biomas em fatias por ângulo
- 🌊 Anel externo: oceanos por ângulo (warm → normal → cold → frozen)
- 🌎 Fora do raio: fallback para gerador vanilla (`MultiNoiseBiomeSource`)

## Lógica principal

Implementada em `PizzaRingBiomeSource#getBiome`:

```java
double dist = Math.sqrt((double) x * x + (double) z * z);

if (dist < centerRadius) return centerBiome;
if (dist < pizzaRadius) return getPizzaBiome(x, z);
if (dist < oceanRadius) {
    Biome pizza = getPizzaBiome(x, z);
    Biome ocean = getOceanBiomeByAngle(x, z);
    return fade < 0.5 ? pizza : ocean;
}

return vanillaBiomeSource.getBiome(x, y, z, noise);
```

Esse fallback é o ponto mais importante: mantém o mundo infinito e normal depois do "hub".

## Arquivos principais

- `src/main/java/dev/mundocs5/world/PizzaRingBiomeSource.java`
- `src/main/java/dev/mundocs5/MundoCs5Mod.java`
- `src/main/resources/data/mundocs5/worldgen/dimension/pizza_overworld.json`

## Como usar

1. Build do mod com Gradle/Loom.
2. Instale no cliente/servidor Fabric 1.21.9 (Java 21).
3. Crie um novo mundo usando o preset `mundocs5:pizza_world`.

## Ajustes rápidos

Você pode mudar no JSON:

- `center_radius`
- `pizza_radius`
- `ocean_radius`
- ordem/lista de `pizza_biomes`
- ordem/lista de `ocean_biomes`

Para ficar ainda mais parecido com seu exemplo, recomendo gerar terreno com mods de noise/erosion e deixar este mod só no controle de biomas.
