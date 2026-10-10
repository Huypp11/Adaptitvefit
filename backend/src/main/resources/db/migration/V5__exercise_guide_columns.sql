-- Noi dung huong dan: cac buoc (moi dong mot buoc), duong dan anh (moi dong mot duong dan), meo, MET de uoc tinh calo
ALTER TABLE exercises
  ADD COLUMN met DOUBLE NULL,
  ADD COLUMN steps VARCHAR(4000) NULL,
  ADD COLUMN media_urls VARCHAR(1000) NULL,
  ADD COLUMN tips VARCHAR(1000) NULL;
