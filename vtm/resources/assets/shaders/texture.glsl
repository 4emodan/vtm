#ifdef GLES
precision highp float;
#endif
attribute vec4 vertex;
attribute vec2 tex_coord;
uniform mat4 u_mvp;
varying vec2 tex_c;
uniform vec2 u_div;
void
main(){
  vec2 dir = vertex.zw;
  gl_Position = u_mvp * vec4(vertex.xy + dir, 0.0, 1.0);
  tex_c = tex_coord * u_div;
}

$$

#ifdef GLES
precision highp float;
#endif
uniform sampler2D tex;
varying vec2 tex_c;
void
main(){
  gl_FragColor = texture2D(tex, tex_c);
}
