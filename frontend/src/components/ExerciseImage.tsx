import { useState } from 'react';

interface Props { media: string[]; alt: string }

/** Hai anh (bat dau / ket thuc) luan phien de mo phong GIF. Khi giam chuyen dong, chi hien anh dau. */
export default function ExerciseImage({ media, alt }: Props) {
  const [failed, setFailed] = useState(false);
  if (media.length === 0 || failed) {
    return <div className="frames frames-empty" role="img" aria-label={alt}>Chưa có ảnh minh họa</div>;
  }
  return (
    <div className="frames" role="img" aria-label={alt}>
      <img src={media[0]} alt="" loading="lazy" onError={() => setFailed(true)} />
      {media[1] && <img className="f2" src={media[1]} alt="" loading="lazy" onError={() => setFailed(true)} />}
    </div>
  );
}
