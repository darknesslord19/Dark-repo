# Sinematv CS3

CloudStream icin cloudstream_super.py ile otomatik uretilen eklenti projesi.

## GitHub Actions

Bu repo GitHub'a yuklendiginde `main` veya `master` branch'e yapilan push ile otomatik derlenir.
Ayrica Actions > Build CloudStream CS3 > Run workflow ile elle baslatilabilir.

Derleme tamamlaninca workflow sayfasindaki **Artifacts** bolumunden `Sinematv-CS3` paketini indirebilirsin.
Icinde her eklenti icin bir `.cs3` dosyasi bulunur.

## Moduller

- `Sinematv` (com.darkneslord.sinematv)
