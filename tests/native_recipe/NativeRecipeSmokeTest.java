import com.google.gson.*;
import com.mojang.serialization.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;

/** Invokes actual vanilla recipe and loot implementations; never starts a server. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class NativeRecipeSmokeTest {
    static final String P = "net.minecraft.";
    static final String TARGET = "simple_terminals:crafting_terminal";
    static final String LOOT_TARGET = "simple_terminals:blocks/crafting_terminal";
    static final Map<String,String> classes = new HashMap<>();
    static final Map<String,String> members = new HashMap<>();
    static String version;
    static Object registry, registries, target;
    static Class<?> idClass, gridClass, stackClass;
    static Object empty;
    static int checks;

    static void loadMappings(Path path) throws Exception {
        String owner = null;
        Pattern header=Pattern.compile("^(\\S+) -> (\\S+):$");
        for(String line:Files.readAllLines(path)) {
            Matcher h=header.matcher(line);
            if(h.matches()){owner=h.group(1);classes.put(owner,h.group(2));continue;}
            if(owner==null || !line.startsWith("    ") || !line.contains(" -> "))continue;
            String[] parts=line.trim().split(" -> ");
            String left=parts[0].replaceFirst("^\\d+:\\d+:","");
            int space=left.indexOf(' ');
            if(space>=0)members.put(owner+"#"+left.substring(space+1),parts[1]);
        }
    }
    static Class<?> cls(String logical) throws Exception {
        if(logical.equals("int"))return int.class;
        if(logical.equals("boolean"))return boolean.class;
        if(logical.equals("long"))return long.class;
        return Class.forName(classes.getOrDefault(logical,logical), false, NativeRecipeSmokeTest.class.getClassLoader());
    }
    static String name(String owner,String signature){return members.getOrDefault(owner+"#"+signature,signature.replaceFirst("\\(.*",""));}
    static Method method(String owner,String logical,String...params) throws Exception {
        Class<?>[] types=new Class<?>[params.length];for(int i=0;i<types.length;i++)types[i]=cls(params[i]);
        Method m=cls(owner).getDeclaredMethod(name(owner,logical+"("+String.join(",",params)+")"),types);m.setAccessible(true);return m;
    }
    static Object call(String owner,String logical,Object receiver,String[]params,Object...args)throws Exception{return method(owner,logical,params).invoke(receiver,args);}
    static Field field(String owner,String logical)throws Exception{Field f=cls(owner).getDeclaredField(name(owner,logical));f.setAccessible(true);return f;}
    static Object id(String s)throws Exception{
        if(version.equals("1.20.1"))return idClass.getConstructor(String.class).newInstance(s);
        return call(idClass.getName().equals(P+"resources.Identifier")?P+"resources.Identifier":P+"resources.ResourceLocation","parse",null,new String[]{"java.lang.String"},s);
    }
    static void bootstrap()throws Exception {
        call(P+"SharedConstants","tryDetectVersion",null,new String[]{});
        String built=P+"core.registries.BuiltInRegistries";
        // Permit registry class construction solely to install a test registration callback.
        field(P+"server.Bootstrap","isBootstrapped").setBoolean(null,true);
        registry=field(built,"ITEM").get(null);
        Map<Object,Supplier<?>> loaders=(Map<Object,Supplier<?>>)field(built,"LOADERS").get(null);
        Object loaderKey=loaders.keySet().stream().filter(k->k.toString().equals("minecraft:item")).findFirst().orElseThrow();
        Supplier<?> original=loaders.get(loaderKey);
        loaders.put(loaderKey,()->{
            Object prior=original.get();
            try {
                String propName=P+"world.item.Item$Properties";
                Object props=cls(propName).getConstructor().newInstance();
                if(version.equals("26.2")) {
                    Object key=call(P+"resources.ResourceKey","create",null,new String[]{P+"resources.ResourceKey",P+"resources.Identifier"},field(P+"core.registries.Registries","ITEM").get(null),id(TARGET));
                    call(propName,"setId",props,new String[]{P+"resources.ResourceKey"},key);
                }
                target=cls(P+"world.item.Item").getConstructor(cls(propName)).newInstance(props);
                call(P+"core.Registry","register",null,new String[]{P+"core.Registry","java.lang.String","java.lang.Object"},registry,TARGET,target);
            }catch(Exception e){throw new RuntimeException(e);}
            return prior;
        });
        field(P+"server.Bootstrap","isBootstrapped").setBoolean(null,false);
        call(P+"server.Bootstrap","bootStrap",null,new String[]{});
        if(target==null)throw new AssertionError("Bootstrap failed to register test stand-in item");
        if(!version.equals("1.20.1"))registries=call(P+"core.RegistryAccess","fromRegistryOfRegistries",null,new String[]{P+"core.Registry"},field(built,"REGISTRY").get(null));
        if(version.equals("26.2")) {
            registries=call(P+"data.registries.VanillaRegistries","createLookup",null,new String[]{});
            Object initializers=field(built,"DATA_COMPONENT_INITIALIZERS").get(null);
            List<?> pending=(List<?>)call(P+"core.component.DataComponentInitializers","build",initializers,new String[]{P+"core.HolderLookup$Provider"},registries);
            for(Object components:pending)call(P+"core.component.DataComponentInitializers$PendingComponents","apply",components,new String[]{});
        }
        empty=field(P+"world.item.ItemStack","EMPTY").get(null);
    }
    static Object parse(JsonObject json)throws Exception {
        if(version.equals("1.20.1"))return call(P+"world.item.crafting.RecipeManager","fromJson",null,new String[]{P+"resources.ResourceLocation","com.google.gson.JsonObject"},id(TARGET),json);
        Codec codec=(Codec)field(P+"world.item.crafting.Recipe","CODEC").get(null);
        DynamicOps ops=(DynamicOps)call(P+"resources.RegistryOps","create",null,new String[]{"com.mojang.serialization.DynamicOps",P+"core.HolderLookup$Provider"},JsonOps.INSTANCE,registries);
        DataResult<?> result=codec.parse(ops,json);
        return result.result().orElseThrow(()->new IllegalArgumentException(result.toString()));
    }
    static Object item(String s)throws Exception {
        return call(P+"core.Registry",version.equals("26.2")?"getValue":"get",registry,new String[]{version.equals("26.2")?P+"resources.Identifier":P+"resources.ResourceLocation"},id(s));
    }
    static Object stack(String name)throws Exception {
        if(name.equals(" "))return empty;
        String s=switch(name){case"R"->"minecraft:redstone_torch";case"D"->"minecraft:diamond";case"T"->"minecraft:crafting_table";case"C"->"minecraft:chest";case"X"->"minecraft:dirt";default->name;};
        return stackClass.getConstructor(cls(P+"world.level.ItemLike")).newInstance(item(s));
    }
    static Object grid(String...rows)throws Exception {
        int width=rows[0].length(),height=rows.length;List<Object> stacks=new ArrayList<>();
        for(String row:rows){if(row.length()!=width)throw new IllegalArgumentException("Unequal grid widths");for(int i=0;i<row.length();i++)stacks.add(stack(row.substring(i,i+1)));}
        if(!version.equals("1.20.1"))return call(P+"world.item.crafting.CraftingInput","of",null,new String[]{"int","int","java.util.List"},width,height,stacks);
        return Proxy.newProxyInstance(gridClass.getClassLoader(),new Class[]{gridClass},(proxy,m,a)->{
            if(m.getName().equals(name(P+"world.inventory.CraftingContainer","getWidth()")))return width;
            if(m.getName().equals(name(P+"world.inventory.CraftingContainer","getHeight()")))return height;
            if(m.getName().equals(name(P+"world.inventory.CraftingContainer","getItems()")))return stacks;
            if(m.getName().equals(name(P+"world.Container","getItem(int)")) && m.getParameterCount()==1)return stacks.get((int)a[0]);
            if(m.getReturnType()==int.class)return stacks.size();
            if(m.getReturnType()==boolean.class)return false;
            return null;
        });
    }
    static boolean matches(Object recipe,Object grid)throws Exception {
        return (boolean)call(P+"world.item.crafting.ShapedRecipe","matches",recipe,new String[]{version.equals("1.20.1")?P+"world.inventory.CraftingContainer":P+"world.item.crafting.CraftingInput",P+"world.level.Level"},grid,null);
    }
    static Object assemble(Object recipe,Object grid)throws Exception {
        if(version.equals("26.2"))return call(P+"world.item.crafting.ShapedRecipe","assemble",recipe,new String[]{P+"world.item.crafting.CraftingInput"},grid);
        return call(P+"world.item.crafting.ShapedRecipe","assemble",recipe,new String[]{version.equals("1.20.1")?P+"world.inventory.CraftingContainer":P+"world.item.crafting.CraftingInput",version.equals("1.20.1")?P+"core.RegistryAccess":P+"core.HolderLookup$Provider"},grid,registries);
    }
    static void assertMatch(Object recipe,String label,boolean expected,String...rows)throws Exception{
        Object g=grid(rows);boolean actual=matches(recipe,g);if(actual!=expected)throw new AssertionError(label+": expected "+expected+", got "+actual);checks++;
        if(expected){Object out=assemble(recipe,g);Object outItem=call(P+"world.item.ItemStack","getItem",out,new String[]{});int count=(int)call(P+"world.item.ItemStack","getCount",out,new String[]{});
            Object key=call(P+"core.Registry","getKey",registry,new String[]{"java.lang.Object"},outItem);
            if(outItem!=target || !key.toString().equals(TARGET) || count!=1)throw new AssertionError(label+": bad output "+key+" x"+count);checks++;}
    }
    static Map<?,?> discover(Path root)throws Exception{return discover(root,false);}
    static Map<?,?> discover(Path root,boolean loot)throws Exception {
        Object pack;
        if(version.equals("1.20.1"))pack=cls(P+"server.packs.PathPackResources").getConstructor(String.class,Path.class,boolean.class).newInstance("simple-terminals-test",root,false);
        else {
            Object title=call(P+"network.chat.Component","literal",null,new String[]{"java.lang.String"},"Native recipe test");
            Object source=field(P+"server.packs.repository.PackSource","DEFAULT").get(null);
            Object location=cls(P+"server.packs.PackLocationInfo").getConstructor(String.class,cls(P+"network.chat.Component"),cls(P+"server.packs.repository.PackSource"),Optional.class).newInstance("simple-terminals-test",title,source,Optional.empty());
            pack=cls(P+"server.packs.PathPackResources").getConstructor(cls(P+"server.packs.PackLocationInfo"),Path.class).newInstance(location,root);
        }
        Object manager=cls(P+"server.packs.resources.MultiPackResourceManager").getConstructor(cls(P+"server.packs.PackType"),List.class).newInstance(field(P+"server.packs.PackType","SERVER_DATA").get(null),List.of(pack));
        try {
            Object converter;
            if(loot) {
                String prefix;
                if(version.equals("1.20.1"))prefix=(String)call(P+"world.level.storage.loot.LootDataType","directory",field(P+"world.level.storage.loot.LootDataType","TABLE").get(null),new String[]{});
                else prefix=(String)call(P+"core.registries.Registries","elementsDirPath",null,new String[]{P+"resources.ResourceKey"},field(P+"core.registries.Registries","LOOT_TABLE").get(null));
                converter=call(P+"resources.FileToIdConverter","json",null,new String[]{"java.lang.String"},prefix);
            }
            else if(version.equals("26.2"))converter=field(P+"world.item.crafting.RecipeManager","RECIPE_LISTER").get(null);
            else {
                Object recipeManager=version.equals("1.20.1")?cls(P+"world.item.crafting.RecipeManager").getConstructor().newInstance():cls(P+"world.item.crafting.RecipeManager").getConstructor(cls(P+"core.HolderLookup$Provider")).newInstance(registries);
                String prefix=(String)field(P+"server.packs.resources.SimpleJsonResourceReloadListener","directory").get(recipeManager);
                converter=call(P+"resources.FileToIdConverter","json",null,new String[]{"java.lang.String"},prefix);
            }
            Map<?,?> resources=(Map<?,?>)call(P+"resources.FileToIdConverter","listMatchingResources",converter,new String[]{P+"server.packs.resources.ResourceManager"},manager);
            Map<String,JsonObject> recipes=new HashMap<>();
            for(Map.Entry<?,?> entry:resources.entrySet()) {
                Object recipeId=call(P+"resources.FileToIdConverter","fileToId",converter,new String[]{version.equals("26.2")?P+"resources.Identifier":P+"resources.ResourceLocation"},entry.getKey());
                try(java.io.Reader reader=(java.io.Reader)call(P+"server.packs.resources.Resource","openAsReader",entry.getValue(),new String[]{})) {
                    recipes.put(recipeId.toString(),JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
            return recipes;
        }finally{call(P+"server.packs.resources.MultiPackResourceManager","close",manager,new String[]{});}
    }
    static Object parseLoot(JsonObject json)throws Exception {
        if(version.equals("1.20.1")) {
            Object type=field(P+"world.level.storage.loot.LootDataType","TABLE").get(null);
            return ((Optional<?>)call(P+"world.level.storage.loot.LootDataType","deserialize",type,new String[]{P+"resources.ResourceLocation","com.google.gson.JsonElement"},id(LOOT_TARGET),json)).orElseThrow(()->new IllegalArgumentException("Native loot serializer rejected table"));
        }
        Codec codec=(Codec)field(P+"world.level.storage.loot.LootTable","DIRECT_CODEC").get(null);
        DynamicOps ops=(DynamicOps)call(P+"resources.RegistryOps","create",null,new String[]{"com.mojang.serialization.DynamicOps",P+"core.HolderLookup$Provider"},JsonOps.INSTANCE,registries);
        DataResult<?> result=codec.parse(ops,json);
        return result.result().orElseThrow(()->new IllegalArgumentException("Native loot codec: "+result));
    }
    static Object lootContext(Object table,Float explosionRadius)throws Exception {
        String builderName=P+"world.level.storage.loot.LootParams$Builder";
        Object builder=cls(builderName).getConstructor(cls(P+"server.level.ServerLevel")).newInstance((Object)null);
        String paramClass=P+(version.equals("26.2")?"util.context.ContextKey":"world.level.storage.loot.parameters.LootContextParam");
        String params=P+"world.level.storage.loot.parameters.LootContextParams";
        // Supply a native, validated BLOCK context without constructing a level.
        Object state=call(P+"world.level.block.Block","defaultBlockState",field(P+"world.level.block.Blocks","CRAFTING_TABLE").get(null),new String[]{});
        Object[] values={field(P+"world.phys.Vec3","ZERO").get(null),state,empty};
        String[] keys={"ORIGIN","BLOCK_STATE","TOOL"};
        for(int i=0;i<keys.length;i++)call(builderName,"withParameter",builder,new String[]{paramClass,"java.lang.Object"},field(params,keys[i]).get(null),values[i]);
        if(explosionRadius!=null)call(builderName,"withParameter",builder,new String[]{paramClass,"java.lang.Object"},field(params,"EXPLOSION_RADIUS").get(null),explosionRadius);
        Object paramSet=call(P+"world.level.storage.loot.LootTable","getParamSet",table,new String[]{});
        if(paramSet!=field(P+"world.level.storage.loot.parameters.LootContextParamSets","BLOCK").get(null))throw new AssertionError("Loot table is not minecraft:block");
        Object lootParams=call(builderName,"create",builder,new String[]{P+(version.equals("26.2")?"util.context.ContextKeySet":"world.level.storage.loot.parameters.LootContextParamSet")},paramSet);
        Object random=call(P+"util.RandomSource","create",null,new String[]{"long"},0L);
        String resolverClass=P+(version.equals("1.20.1")?"world.level.storage.loot.LootDataResolver":"core.HolderGetter$Provider");
        Constructor<?> constructor=cls(P+"world.level.storage.loot.LootContext").getDeclaredConstructor(cls(P+"world.level.storage.loot.LootParams"),cls(P+"util.RandomSource"),cls(resolverClass));
        constructor.setAccessible(true);
        Object resolver=version.equals("1.20.1")?null:version.equals("1.21.1")?call(P+"core.HolderLookup$Provider","asGetterLookup",registries,new String[]{}):registries;
        return constructor.newInstance(lootParams,random,resolver);
    }
    static void assertDrop(Object table,String label,Float explosionRadius,int expectedStacks)throws Exception {
        List<Object> drops=new ArrayList<>();
        call(P+"world.level.storage.loot.LootTable","getRandomItemsRaw",table,new String[]{P+"world.level.storage.loot.LootContext","java.util.function.Consumer"},lootContext(table,explosionRadius),(Consumer<Object>)drops::add);
        if(drops.size()!=expectedStacks)throw new AssertionError(label+": expected "+expectedStacks+" loot stacks, got "+drops.size());checks++;
        for(Object drop:drops) {
            Object outItem=call(P+"world.item.ItemStack","getItem",drop,new String[]{});
            int count=(int)call(P+"world.item.ItemStack","getCount",drop,new String[]{});
            Object key=call(P+"core.Registry","getKey",registry,new String[]{"java.lang.Object"},outItem);
            if(outItem!=target || !key.toString().equals(TARGET) || count!=1)throw new AssertionError(label+": bad loot output "+key+" x"+count);checks++;
        }
    }
    static void checkLoot(Path resourceRoot,Path baselinePath)throws Exception {
        Map<?,?> found=discover(resourceRoot,true);
        if(!found.containsKey(LOOT_TARGET))throw new AssertionError("Native resource manager cannot discover fixed loot table "+LOOT_TARGET);checks++;
        Object table=parseLoot((JsonObject)found.get(LOOT_TARGET));
        if(!cls(P+"world.level.storage.loot.LootTable").isInstance(table))throw new AssertionError("Not a native LootTable");checks++;
        assertDrop(table,"normal block drop",null,1);
        assertDrop(table,"unit-radius explosion drop",1.0F,1);
        assertDrop(table,"non-surviving explosion",Float.POSITIVE_INFINITY,0);
        Path baselineRoot=Files.createTempDirectory("native-loot-baseline-");
        try {
            Path oldLoot=baselineRoot.resolve("data/simple_terminals/loot_tables/blocks/crafting_terminal.json");
            Files.createDirectories(oldLoot.getParent());Files.copy(baselinePath,oldLoot);
            boolean visible=discover(baselineRoot,true).containsKey(LOOT_TARGET);
            if(visible!=version.equals("1.20.1"))throw new AssertionError("Unexpected legacy loot directory visibility: "+visible);checks++;
            // The schema itself remains valid; it is the legacy directory that breaks loading.
            parseLoot(JsonParser.parseString(Files.readString(baselinePath)).getAsJsonObject());checks++;
            System.out.println("Native loot discovery: fixed=true, legacy plural path="+visible+"; normal drop="+TARGET+" x1");
        }finally{
            try(var paths=Files.walk(baselineRoot)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    static void checkDiscovery(Path recipePath,Path baselinePath)throws Exception {
        Path resourceRoot=recipePath.getParent().getParent().getParent().getParent();
        Map<?,?> found=discover(resourceRoot);
        if(!found.containsKey(TARGET))throw new AssertionError("Native resource manager cannot discover fixed recipe "+TARGET);checks++;
        parse((JsonObject)found.get(TARGET));checks++;
        Path baselineRoot=Files.createTempDirectory("native-recipe-baseline-");
        try {
            Path oldRecipe=baselineRoot.resolve("data/simple_terminals/recipes/crafting_terminal.json");
            Files.createDirectories(oldRecipe.getParent());Files.copy(baselinePath,oldRecipe);
            boolean visible=discover(baselineRoot).containsKey(TARGET);
            if(visible!=version.equals("1.20.1"))throw new AssertionError("Unexpected legacy directory visibility: "+visible);checks++;
            System.out.println("Native recipe discovery: fixed=true, legacy plural path="+visible);
        }finally{
            try(var paths=Files.walk(baselineRoot)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    static void checkPack(Path metadataPath,Path baselinePath)throws Exception {
        JsonObject pack=JsonParser.parseString(Files.readString(metadataPath)).getAsJsonObject().getAsJsonObject("pack");
        JsonObject baseline=JsonParser.parseString(Files.readString(baselinePath)).getAsJsonObject().getAsJsonObject("pack");
        for(String side:new String[]{"CLIENT_TYPE","SERVER_TYPE"}) {
            Object sectionType=field(P+"server.packs.metadata.pack.PackMetadataSection",side).get(null);
            Codec codec=(Codec)call(P+"server.packs.metadata.MetadataSectionType","codec",sectionType,new String[]{});
            DataResult<?> parsed=codec.parse(JsonOps.INSTANCE,pack);
            Object section=parsed.result().orElseThrow(()->new IllegalArgumentException("Pack metadata: "+parsed));
            Object range=call(P+"server.packs.metadata.pack.PackMetadataSection","supportedFormats",section,new String[]{});
            Object format=cls(P+"server.packs.metadata.pack.PackFormat").getConstructor(int.class,int.class).newInstance(side.equals("CLIENT_TYPE")?88:107,side.equals("CLIENT_TYPE")?0:1);
            Object compatibility=call(P+"server.packs.repository.PackCompatibility","forVersion",null,new String[]{P+"util.InclusiveRange",P+"server.packs.metadata.pack.PackFormat"},range,format);
            if(!compatibility.toString().equals("COMPATIBLE"))throw new AssertionError(side+" pack is "+compatibility);checks++;
            DataResult<?> old=codec.parse(JsonOps.INSTANCE,baseline);
            if(old.result().isPresent()) {
                Object oldRange=call(P+"server.packs.metadata.pack.PackMetadataSection","supportedFormats",old.result().get(),new String[]{});
                Object oldCompat=call(P+"server.packs.repository.PackCompatibility","forVersion",null,new String[]{P+"util.InclusiveRange",P+"server.packs.metadata.pack.PackFormat"},oldRange,format);
                if(oldCompat.toString().equals("COMPATIBLE"))throw new AssertionError("Legacy pack unexpectedly compatible");
                System.out.println("EXPECTED baseline "+side+" pack compatibility: "+oldCompat);
            } else System.out.println("EXPECTED baseline "+side+" pack rejection: "+old);
            checks++;
        }
    }
    public static void main(String[]args)throws Exception {
        version=args[0];if(!args[1].equals("-"))loadMappings(Path.of(args[1]));
        idClass=cls(P+"resources."+(version.equals("26.2")?"Identifier":"ResourceLocation"));
        stackClass=cls(P+"world.item.ItemStack");
        gridClass=cls(P+(version.equals("1.20.1")?"world.inventory.CraftingContainer":"world.item.crafting.CraftingInput"));
        bootstrap();
        Object recipe=parse(JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject());
        if(!cls(P+"world.item.crafting.ShapedRecipe").isInstance(recipe))throw new AssertionError("Not a native shaped recipe");checks++;
        for(boolean mirror:new boolean[]{false,true})for(int y:new int[]{0,1}){
            String[]rows=y==0?new String[]{" R ",mirror?"CTD":"DTC","   "}:new String[]{"   "," R ",mirror?"CTD":"DTC"};
            assertMatch(recipe,"valid mirror="+mirror+" y="+y,true,rows);
        }
        assertMatch(recipe,"missing torch",false,"   ","DTC","   ");
        assertMatch(recipe,"wrong torch",false," X ","DTC","   ");
        assertMatch(recipe,"wrong table",false," R ","DXC","   ");
        assertMatch(recipe,"missing diamond",false," R "," TC","   ");
        assertMatch(recipe,"missing chest",false," R ","DT ","   ");
        assertMatch(recipe,"extra item inside bounding box",false,"XR ","DTC","   ");
        assertMatch(recipe,"extra item outside bounding box",false," R ","DTC"," X ");
        assertMatch(recipe,"arbitrary order",false," R ","TDC","   ");
        assertMatch(recipe,"too small",false," R","DT");
        assertMatch(recipe,"vertical flip",false,"DTC"," R ","   ");
        if(args.length>3){
            checkDiscovery(Path.of(args[2]),Path.of(args[3]));
            JsonObject baseline=JsonParser.parseString(Files.readString(Path.of(args[3]))).getAsJsonObject();
            if(!version.equals("1.20.1")){
                boolean rejected=false;try{parse(baseline);}catch(Exception|AssertionError e){rejected=true;System.out.println("EXPECTED baseline parse rejection: "+e.getMessage());}
                if(!rejected)throw new AssertionError("Legacy baseline was unexpectedly accepted");checks++;
            }
        }
        if(version.equals("26.2") && args.length>5)checkPack(Path.of(args[4]),Path.of(args[5]));
        if(args.length>7)checkLoot(Path.of(args[6]),Path.of(args[7]));
        System.out.println("PASS "+version+": "+checks+" native recipe/loot codec, discovery, grid and output checks; test stand-in item="+TARGET);
        System.exit(0);
    }
}
