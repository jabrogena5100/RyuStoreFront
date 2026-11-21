# Lighthouse A/B: photos-ab

- Lighthouse 13.6.0, HeadlessChrome/159.0.0.0, run 2026-11-21
- **Before:** commit `0e1f1ef`. **After:** `2753d57 + uncommitted changes in web/`. Both production builds (`next build` + `next start`, ports 3001 and 3000), served at the same time.
- Both against the same running API at `http://localhost:8080` (the real Spring API with the seed profile, Postgres and SeaweedFS in Compose). Product page: `/products/ostrava-atelier-umber-chelsea-boot`.
- Machine: RyuStoreFront, 4 CPUs. Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.
- Runs interleaved: for each run, page and form factor, before and after back to back, alternating which goes first. Page weight = transferred bytes (compressed).
- Raw reports: `evidence-raw/lighthouse/photos-ab-before/` and `-after/` (git-ignored).

## Medians (min–max)

| Page | Form factor | Build | Runs | Perf | A11y | BP | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | before | 5 | 90 (78–91) | 100 | 96 | 388 (380–564) | 388 (380–564) | 263 (240–472) | 0.000 | 276.1 (276.1–276.7) | 156.1 | 0.0 | 45 (45–46) |
| home | desktop | after | 5 | 91 (83–99) | 100 | 100 | 352 (321–446) | 352 (321–446) | 252 (90–360) | 0.000 | 465.3 (465.3–465.9) | 156.5 | 185.9 | 62 (62–63) |
| home | mobile | before | 5 | 72 (71–77) | 100 | 96 | 959 (930–1398) | 959 (930–1398) | 1918 (1147–2792) | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | after | 5 | 73 (69–75) | 100 | 100 | 942 (935–1013) | 2452 (1910–2602) | 1427 (1304–2066) | 0.000 | 465.4 (458.0–466.0) | 156.5 | 199.5 (191.5–199.5) | 50 (50–51) |
| listing | desktop | before | 5 | 93 (92–97) | 100 | 96 | 318 (309–329) | 318 (309–329) | 207 (153–225) | 0.000 | 306.6 (306.0–306.6) | 155.0 | 0.0 | 61 (60–61) |
| listing | desktop | after | 5 | 95 (93–98) | 100 | 100 | 348 (316–394) | 1038 (589–1086) | 146 (111–202) | 0.000 | 560.4 (560.3–560.4) | 155.3 | 254.2 | 89 |
| listing | mobile | before | 5 | 71 (58–74) | 100 | 96 | 963 (937–1451) | 1291 (937–3997) | 2296 (1463–2802) | 0.000 | 273.9 (273.3–273.9) | 155.0 | 0.0 | 34 (33–34) |
| listing | mobile | after | 5 | 60 (56–69) | 100 | 100 | 937 (928–1259) | 4459 (2919–4711) | 1579 (1171–2664) | 0.000 | 525.1 (524.5–525.1) | 155.3 | 254.2 | 60 (59–60) |
| product | desktop | before | 5 | 93 (88–99) | 100 | 96 | 347 (313–388) | 347 (313–388) | 218 (107–296) | 0.000 | 258.3 (258.3–259.5) | 156.0 | 0.0 | 35 (35–37) |
| product | desktop | after | 5 | 96 (93–99) | 100 | 100 | 336 (298–368) | 942 (574–994) | 169 (106–195) | 0.000 (0.000–0.005) | 437.9 (429.3–437.9) | 156.4 | 173.3 (165.4–173.3) | 51 (49–51) |
| product | mobile | before | 5 | 74 (73–78) | 100 | 96 | 811 (801–1434) | 811 (801–1434) | 1483 (995–1783) | 0.000 | 253.5 (252.9–253.5) | 156.0 | 0.0 | 32 (31–32) |
| product | mobile | after | 5 | 75 (57–76) | 100 | 100 | 966 (818–1265) | 2724 (2135–4613) | 1160 (882–1699) | 0.000 | 426.8 (418.8–426.8) | 156.4 | 173.3 (165.4–173.3) | 42 (41–42) |
| sign-in | desktop | before | 5 | 96 (92–97) | 100 | 96 | 303 (283–323) | 303 (283–323) | 170 (155–222) | 0.000 | 255.8 (255.2–255.8) | 156.9 | 0.0 | 37 (36–37) |
| sign-in | desktop | after | 5 | 94 (89–98) | 100 | 100 | 330 (278–410) | 875 (755–898) | 198 (110–257) | 0.000 (0.000–0.075) | 281.0 (280.5–281.6) | 157.2 | 25.1 | 37 (36–38) |
| sign-in | mobile | before | 5 | 74 (72–77) | 100 | 96 | 943 (926–1029) | 943 (926–1029) | 1465 (1130–2370) | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | after | 5 | 75 (72–78) | 100 | 100 | 936 (927–1374) | 936 (927–1374) | 1312 (1056–1906) | 0.000 | 249.3 (248.1–249.3) | 157.2 | 0.0 | 31 (29–31) |

## Paired differences (after − before, same run number)

Median of the per-pair differences, then how many pairs got better / worse / stayed equal. A direction is only worth
quoting when (nearly) all pairs agree; a split means the runs cannot tell the builds apart.

| Page | Form factor | Pairs | Perf | LCP ms | TBT ms | CLS | Total KB | Img KB | BP |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 5 | +5 (4↑ 0↓ 1=) | -41 (3↑ 2↓ 0=) | -112 (4↑ 1↓ 0=) | +0.000 (0↑ 0↓ 5=) | +189.2 (0↑ 5↓ 0=) | +185.9 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| home | mobile | 5 | -2 (2↑ 3↓ 0=) | +1181 (0↑ 5↓ 0=) | +149 (2↑ 3↓ 0=) | +0.000 (0↑ 0↓ 5=) | +202.2 (0↑ 5↓ 0=) | +199.5 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| listing | desktop | 5 | +1 (3↑ 1↓ 1=) | +728 (0↑ 5↓ 0=) | -34 (5↑ 0↓ 0=) | +0.000 (0↑ 0↓ 5=) | +253.7 (0↑ 5↓ 0=) | +254.2 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| listing | mobile | 5 | -14 (1↑ 4↓ 0=) | +3260 (0↑ 5↓ 0=) | -678 (4↑ 1↓ 0=) | +0.000 (0↑ 0↓ 5=) | +251.2 (0↑ 5↓ 0=) | +254.2 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| product | desktop | 5 | +3 (3↑ 2↓ 0=) | +606 (0↑ 5↓ 0=) | -49 (3↑ 2↓ 0=) | +0.000 (0↑ 1↓ 4=) | +178.4 (0↑ 5↓ 0=) | +173.3 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| product | mobile | 5 | -1 (2↑ 3↓ 0=) | +1391 (0↑ 5↓ 0=) | -393 (4↑ 1↓ 0=) | +0.000 (0↑ 0↓ 5=) | +173.3 (0↑ 5↓ 0=) | +173.3 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| sign-in | desktop | 5 | -2 (1↑ 4↓ 0=) | +572 (0↑ 5↓ 0=) | +12 (2↑ 3↓ 0=) | +0.000 (0↑ 1↓ 4=) | +25.3 (0↑ 5↓ 0=) | +25.1 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| sign-in | mobile | 5 | +2 (3↑ 1↓ 1=) | +10 (2↑ 3↓ 0=) | -281 (4↑ 1↓ 0=) | +0.000 (0↑ 0↓ 5=) | +0.8 (1↑ 4↓ 0=) | +0.0 (0↑ 0↓ 5=) | +4 (5↑ 0↓ 0=) |

## Console errors Lighthouse recorded (any run)

- **before**, home: `network` Failed to load resource: the server responded with a status of 401 (Unauthorized)
- **before**, listing: `network` Failed to load resource: the server responded with a status of 401 (Unauthorized)
- **before**, product: `network` Failed to load resource: the server responded with a status of 401 (Unauthorized)
- **before**, sign-in: `network` Failed to load resource: the server responded with a status of 401 (Unauthorized)
- **after:** none

## Per run

| Page | Form factor | Run | Build | Perf | A11y | BP | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 1 | before | 84 | 100 | 96 | 442 | 442 | 346 | 0.000 | 276.7 | 156.1 | 0.0 | 46 |
| home | desktop | 1 | after | 89 | 100 | 100 | 446 | 446 | 264 | 0.000 | 465.3 | 156.5 | 185.9 | 62 |
| home | desktop | 2 | before | 90 | 100 | 96 | 388 | 388 | 263 | 0.000 | 276.1 | 156.1 | 0.0 | 45 |
| home | desktop | 2 | after | 98 | 100 | 100 | 347 | 347 | 132 | 0.000 | 465.3 | 156.5 | 185.9 | 62 |
| home | desktop | 3 | before | 78 | 100 | 96 | 564 | 564 | 472 | 0.000 | 276.7 | 156.1 | 0.0 | 46 |
| home | desktop | 3 | after | 83 | 100 | 100 | 352 | 352 | 360 | 0.000 | 465.3 | 156.5 | 185.9 | 62 |
| home | desktop | 4 | before | 91 | 100 | 96 | 384 | 384 | 240 | 0.000 | 276.1 | 156.1 | 0.0 | 45 |
| home | desktop | 4 | after | 91 | 100 | 100 | 384 | 384 | 252 | 0.000 | 465.3 | 156.5 | 185.9 | 62 |
| home | desktop | 5 | before | 90 | 100 | 96 | 380 | 380 | 260 | 0.000 | 276.1 | 156.1 | 0.0 | 45 |
| home | desktop | 5 | after | 99 | 100 | 100 | 321 | 321 | 90 | 0.000 | 465.9 | 156.5 | 185.9 | 63 |
| home | mobile | 1 | before | 71 | 100 | 96 | 1398 | 1398 | 2792 | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | 1 | after | 73 | 100 | 100 | 961 | 2452 | 1304 | 0.000 | 465.4 | 156.5 | 199.5 | 50 |
| home | mobile | 2 | before | 71 | 100 | 96 | 959 | 959 | 2491 | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | 2 | after | 73 | 100 | 100 | 1013 | 2140 | 1427 | 0.000 | 458.0 | 156.5 | 191.5 | 50 |
| home | mobile | 3 | before | 72 | 100 | 96 | 932 | 932 | 1918 | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | 3 | after | 70 | 100 | 100 | 941 | 2494 | 2066 | 0.000 | 466.0 | 156.5 | 199.5 | 51 |
| home | mobile | 4 | before | 77 | 100 | 96 | 930 | 930 | 1147 | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | 4 | after | 75 | 100 | 100 | 935 | 1910 | 1327 | 0.000 | 465.4 | 156.5 | 199.5 | 50 |
| home | mobile | 5 | before | 73 | 100 | 96 | 1391 | 1391 | 1550 | 0.000 | 263.2 | 156.1 | 0.0 | 34 |
| home | mobile | 5 | after | 69 | 100 | 100 | 942 | 2602 | 2048 | 0.000 | 465.9 | 156.5 | 199.5 | 51 |
| listing | desktop | 1 | before | 92 | 100 | 96 | 329 | 329 | 225 | 0.000 | 306.0 | 155.0 | 0.0 | 60 |
| listing | desktop | 1 | after | 93 | 100 | 100 | 348 | 1086 | 191 | 0.000 | 560.4 | 155.3 | 254.2 | 89 |
| listing | desktop | 2 | before | 97 | 100 | 96 | 314 | 314 | 153 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 2 | after | 98 | 100 | 100 | 316 | 948 | 111 | 0.000 | 560.4 | 155.3 | 254.2 | 89 |
| listing | desktop | 3 | before | 96 | 100 | 96 | 329 | 329 | 164 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 3 | after | 95 | 100 | 100 | 394 | 1079 | 144 | 0.000 | 560.4 | 155.3 | 254.2 | 89 |
| listing | desktop | 4 | before | 93 | 100 | 96 | 309 | 309 | 218 | 0.000 | 306.0 | 155.0 | 0.0 | 60 |
| listing | desktop | 4 | after | 95 | 100 | 100 | 339 | 1038 | 146 | 0.000 | 560.3 | 155.3 | 254.2 | 89 |
| listing | desktop | 5 | before | 93 | 100 | 96 | 318 | 318 | 207 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 5 | after | 93 | 100 | 100 | 389 | 589 | 202 | 0.000 | 560.4 | 155.3 | 254.2 | 89 |
| listing | mobile | 1 | before | 71 | 100 | 96 | 937 | 937 | 2296 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 1 | after | 69 | 100 | 100 | 948 | 2919 | 1618 | 0.000 | 524.5 | 155.3 | 254.2 | 59 |
| listing | mobile | 2 | before | 72 | 100 | 96 | 963 | 963 | 1987 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 2 | after | 56 | 100 | 100 | 1259 | 4286 | 2664 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 3 | before | 70 | 100 | 96 | 1451 | 1451 | 2307 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 3 | after | 56 | 100 | 100 | 931 | 4711 | 1579 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 4 | before | 58 | 100 | 96 | 948 | 3997 | 2802 | 0.000 | 273.3 | 155.0 | 0.0 | 33 |
| listing | mobile | 4 | after | 61 | 100 | 100 | 928 | 4459 | 1171 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 5 | before | 74 | 100 | 96 | 1291 | 1291 | 1463 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 5 | after | 60 | 100 | 100 | 937 | 4637 | 1182 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| product | desktop | 1 | before | 99 | 100 | 96 | 326 | 326 | 107 | 0.000 | 258.3 | 156.0 | 0.0 | 35 |
| product | desktop | 1 | after | 96 | 100 | 100 | 368 | 942 | 148 | 0.000 | 429.9 | 156.4 | 165.4 | 50 |
| product | desktop | 2 | before | 93 | 100 | 96 | 347 | 347 | 218 | 0.000 | 258.9 | 156.0 | 0.0 | 36 |
| product | desktop | 2 | after | 96 | 100 | 100 | 358 | 574 | 169 | 0.005 | 429.3 | 156.4 | 165.4 | 49 |
| product | desktop | 3 | before | 88 | 100 | 96 | 388 | 388 | 296 | 0.000 | 258.3 | 156.0 | 0.0 | 35 |
| product | desktop | 3 | after | 99 | 100 | 100 | 336 | 620 | 106 | 0.000 | 437.9 | 156.4 | 173.3 | 51 |
| product | desktop | 4 | before | 98 | 100 | 96 | 313 | 313 | 128 | 0.000 | 258.3 | 156.0 | 0.0 | 35 |
| product | desktop | 4 | after | 95 | 100 | 100 | 298 | 948 | 170 | 0.000 | 437.9 | 156.4 | 173.3 | 51 |
| product | desktop | 5 | before | 90 | 100 | 96 | 388 | 388 | 258 | 0.000 | 259.5 | 156.0 | 0.0 | 37 |
| product | desktop | 5 | after | 93 | 100 | 100 | 314 | 994 | 195 | 0.000 | 437.9 | 156.4 | 173.3 | 51 |
| product | mobile | 1 | before | 73 | 100 | 96 | 1434 | 1434 | 1783 | 0.000 | 253.5 | 156.0 | 0.0 | 32 |
| product | mobile | 1 | after | 75 | 100 | 100 | 1265 | 2825 | 882 | 0.000 | 418.8 | 156.4 | 165.4 | 41 |
| product | mobile | 2 | before | 74 | 100 | 96 | 819 | 819 | 1483 | 0.000 | 253.5 | 156.0 | 0.0 | 32 |
| product | mobile | 2 | after | 76 | 100 | 100 | 818 | 2136 | 1080 | 0.000 | 426.8 | 156.4 | 173.3 | 42 |
| product | mobile | 3 | before | 74 | 100 | 96 | 811 | 811 | 1578 | 0.000 | 253.5 | 156.0 | 0.0 | 32 |
| product | mobile | 3 | after | 72 | 100 | 100 | 1179 | 2724 | 1185 | 0.000 | 426.8 | 156.4 | 173.3 | 42 |
| product | mobile | 4 | before | 76 | 100 | 96 | 810 | 810 | 1169 | 0.000 | 252.9 | 156.0 | 0.0 | 31 |
| product | mobile | 4 | after | 75 | 100 | 100 | 966 | 2135 | 1160 | 0.000 | 418.8 | 156.4 | 165.4 | 41 |
| product | mobile | 5 | before | 78 | 100 | 96 | 801 | 801 | 995 | 0.000 | 253.5 | 156.0 | 0.0 | 32 |
| product | mobile | 5 | after | 57 | 100 | 100 | 828 | 4613 | 1699 | 0.000 | 426.8 | 156.4 | 173.3 | 42 |
| sign-in | desktop | 1 | before | 97 | 100 | 96 | 313 | 313 | 155 | 0.000 | 255.8 | 156.9 | 0.0 | 37 |
| sign-in | desktop | 1 | after | 89 | 100 | 100 | 330 | 897 | 257 | 0.000 | 281.0 | 157.2 | 25.1 | 37 |
| sign-in | desktop | 2 | before | 92 | 100 | 96 | 283 | 283 | 222 | 0.000 | 255.8 | 156.9 | 0.0 | 37 |
| sign-in | desktop | 2 | after | 91 | 100 | 100 | 410 | 898 | 198 | 0.075 | 280.5 | 157.2 | 25.1 | 36 |
| sign-in | desktop | 3 | before | 96 | 100 | 96 | 323 | 323 | 170 | 0.000 | 255.8 | 156.9 | 0.0 | 37 |
| sign-in | desktop | 3 | after | 98 | 100 | 100 | 278 | 755 | 110 | 0.000 | 281.6 | 157.2 | 25.1 | 38 |
| sign-in | desktop | 4 | before | 96 | 100 | 96 | 288 | 288 | 164 | 0.000 | 255.2 | 156.9 | 0.0 | 36 |
| sign-in | desktop | 4 | after | 94 | 100 | 100 | 332 | 812 | 198 | 0.000 | 281.6 | 157.2 | 25.1 | 38 |
| sign-in | desktop | 5 | before | 96 | 100 | 96 | 303 | 303 | 171 | 0.000 | 255.8 | 156.9 | 0.0 | 37 |
| sign-in | desktop | 5 | after | 94 | 100 | 100 | 308 | 875 | 183 | 0.000 | 281.0 | 157.2 | 25.1 | 37 |
| sign-in | mobile | 1 | before | 75 | 100 | 96 | 943 | 943 | 1337 | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | 1 | after | 78 | 100 | 100 | 931 | 931 | 1056 | 0.000 | 249.3 | 157.2 | 0.0 | 31 |
| sign-in | mobile | 2 | before | 72 | 100 | 96 | 944 | 944 | 2370 | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | 2 | after | 72 | 100 | 100 | 1374 | 1374 | 1906 | 0.000 | 249.3 | 157.2 | 0.0 | 31 |
| sign-in | mobile | 3 | before | 77 | 100 | 96 | 1029 | 1029 | 1130 | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | 3 | after | 74 | 100 | 100 | 1069 | 1069 | 1408 | 0.000 | 248.1 | 157.2 | 0.0 | 29 |
| sign-in | mobile | 4 | before | 74 | 100 | 96 | 926 | 926 | 1465 | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | 4 | after | 76 | 100 | 100 | 936 | 936 | 1232 | 0.000 | 249.3 | 157.2 | 0.0 | 31 |
| sign-in | mobile | 5 | before | 72 | 100 | 96 | 933 | 933 | 2312 | 0.000 | 248.5 | 156.9 | 0.0 | 31 |
| sign-in | mobile | 5 | after | 75 | 100 | 100 | 927 | 927 | 1312 | 0.000 | 249.3 | 157.2 | 0.0 | 31 |
