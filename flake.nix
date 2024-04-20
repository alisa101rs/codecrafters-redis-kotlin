{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    devenv.url = "github:cachix/devenv/9ba9e3b908a12ddc6c43f88c52f2bf3c1d1e82c1";
  };

  outputs = { self, nixpkgs, devenv, ... } @ inputs:
    let
      system = builtins.currentSystem;
      pkgs = import nixpkgs { inherit system; };
    in
    {
      devShell.${ system } = devenv.lib.mkShell {
        inherit inputs pkgs;
        modules = [
          ({ pkgs, ... }: {
            packages = [
              pkgs.git
              pkgs.redis
              pkgs.just
              pkgs.detekt
            ];

            languages.java = {
              enable = true;
              jdk.package = pkgs.jdk21;
              maven.enable = true;
            };
            processes.master.exec = "local/spawn.sh";
            processes.replica.exec = "local/spawn.sh --port 6380 --replicaof localhost 6379";
          })
        ];
      };
    };
}
